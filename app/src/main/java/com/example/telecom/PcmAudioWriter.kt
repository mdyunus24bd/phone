package com.example.telecom

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Adaptive software Automatic Gain Control (AGC) + soft limiter for PCM-16 mono audio.
 * Gently boosts quiet remote-party voice pickup during call recording without amplifying
 * silence or causing harsh digital clipping on near-end speech.
 */
internal class AdaptiveCallVoiceGain(
    private val maxGain: Float = 4.2f,
    private val targetPeak: Float = 17_500f,
    private val noiseGatePeak: Int = 24
) {
    private var currentGain = 1.4f

    /**
     * Processes [count] samples in [samples] in-place and returns the normalized RMS/peak
     * level in [0f, 1f] for live UI signal metering.
     */
    fun processInPlace(samples: ShortArray, count: Int): Float {
        if (count <= 0) return 0f

        var rawPeak = 0
        for (i in 0 until count) {
            val a = abs(samples[i].toInt())
            if (a > rawPeak) rawPeak = a
        }

        // Preserve pure silence / sub-noise floor without boosting hiss
        if (rawPeak < noiseGatePeak) {
            currentGain = currentGain * 0.96f + 1.0f * 0.04f
            return (rawPeak / 16_384f).coerceIn(0f, 1f)
        }

        val desiredGain = (targetPeak / rawPeak.toFloat()).coerceIn(1.0f, maxGain)
        // Fast attack to prevent clipping loud spikes; smooth slow release to lift quiet far-end voice
        val alpha = if (desiredGain < currentGain) 0.42f else 0.09f
        currentGain += (desiredGain - currentGain) * alpha

        var sumSquares = 0.0
        for (i in 0 until count) {
            val scaled = samples[i].toFloat() * currentGain
            val limited = softLimitPcm16(scaled)
            val intSample = limited.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            samples[i] = intSample.toShort()
            sumSquares += intSample.toDouble() * intSample.toDouble()
        }

        val rms = sqrt(sumSquares / count.toDouble()).toFloat()
        return (rms / 8_500f).coerceIn(0f, 1f)
    }

    private fun softLimitPcm16(sample: Float): Float {
        val threshold = 28_500f
        val absVal = abs(sample)
        if (absVal <= threshold) return sample
        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absVal - threshold) / (32_767f - threshold)
        // Smooth asymptotic knee between 28500 and 32760
        val compressed = threshold + (32_760f - threshold) * (excess / (1f + excess))
        return sign * compressed
    }
}

/**
 * Abstraction for encoding live PCM-16 mono chunks directly to disk.
 */
internal interface PcmAudioFileWriter {
    val outputFile: File
    val fileExtension: String
    fun writePcm(samples: ShortArray, count: Int)
    fun finishAndClose()
    fun abortAndDelete()
}

/**
 * Thread-safe hardware/software AAC-LC encoder writing standards-compliant MPEG-4 (.m4a)
 * files using Android's MediaCodec encoder paired with a pure-Kotlin ISO BMFF (MP4/M4A)
 * container writer.
 *
 * Bypasses Android's native MediaMuxer / Stagefright MPEG4Writer completely, eliminating
 * native `MPEG4Writer: Stop() called but track is not started or stopped` errors across
 * all Android versions, short captures, and abrupt call disconnects.
 */
internal class AacM4aPcmWriter(
    override val outputFile: File,
    private val sampleRate: Int,
    private val bitRate: Int = if (sampleRate <= 16_000) 64_000 else 96_000
) : PcmAudioFileWriter {

    override val fileExtension: String = "m4a"

    private val lock = Any()
    private var codec: MediaCodec? = null
    private var m4aContainer: PureM4aContainerWriter? = null
    private var audioSpecificConfig: ByteArray? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var totalSamplesQueued = 0L
    private var closed = false

    init {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, max(sampleRate / 2, 16_384))
        }
        val createdCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        try {
            createdCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            createdCodec.start()
            codec = createdCodec
        } catch (e: Exception) {
            runCatching { createdCodec.stop() }
            runCatching { createdCodec.release() }
            throw e
        }
    }

    override fun writePcm(samples: ShortArray, count: Int) {
        if (count <= 0) return
        synchronized(lock) {
            val activeCodec = codec ?: return
            if (closed) return

            val totalBytes = count * 2
            val pcmBytes = ByteArray(totalBytes)
            var byteOffset = 0
            for (i in 0 until count) {
                val s = samples[i].toInt()
                pcmBytes[byteOffset++] = (s and 0xFF).toByte()
                pcmBytes[byteOffset++] = ((s shr 8) and 0xFF).toByte()
            }

            var writtenBytes = 0
            while (writtenBytes < totalBytes && !closed) {
                val inputIndex = activeCodec.dequeueInputBuffer(10_000L)
                if (inputIndex >= 0) {
                    val inputBuffer = activeCodec.getInputBuffer(inputIndex) ?: break
                    inputBuffer.clear()
                    val chunkBytes = min(inputBuffer.remaining(), totalBytes - writtenBytes)
                    inputBuffer.put(pcmBytes, writtenBytes, chunkBytes)
                    val ptsUs = (totalSamplesQueued * 1_000_000L) / sampleRate.toLong()
                    activeCodec.queueInputBuffer(inputIndex, 0, chunkBytes, ptsUs, 0)
                    totalSamplesQueued += (chunkBytes / 2).toLong()
                    writtenBytes += chunkBytes
                }
                drainOutputLocked(activeCodec, endOfStream = false)
            }
        }
    }

    override fun finishAndClose() {
        synchronized(lock) {
            if (closed) return
            closed = true
            val activeCodec = codec
            codec = null

            try {
                if (activeCodec != null && totalSamplesQueued > 0L) {
                    // Ensure at least one full AAC-LC frame (1024 samples) is queued so short captures
                    // flush cleanly instead of producing an empty file
                    if (totalSamplesQueued < 1024L) {
                        val padShorts = (1024L - totalSamplesQueued).toInt()
                        queueSilencePadLocked(activeCodec, padShorts)
                    }
                    queueEndOfStreamLocked(activeCodec)
                    drainOutputLocked(activeCodec, endOfStream = true)
                }
            } catch (_: Exception) {
            } finally {
                releaseCodecAndContainerLocked(activeCodec, finalizeContainer = true)
            }
        }
    }

    override fun abortAndDelete() {
        synchronized(lock) {
            if (closed) {
                runCatching { outputFile.delete() }
                return
            }
            closed = true
            val activeCodec = codec
            codec = null
            releaseCodecAndContainerLocked(activeCodec, finalizeContainer = false)
            runCatching { outputFile.delete() }
        }
    }

    private fun queueSilencePadLocked(activeCodec: MediaCodec, padShorts: Int) {
        val padBytes = padShorts * 2
        val deadline = System.currentTimeMillis() + 200L
        while (System.currentTimeMillis() < deadline) {
            val inputIndex = activeCodec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = activeCodec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                val size = min(inputBuffer.remaining(), padBytes)
                inputBuffer.put(ByteArray(size))
                val ptsUs = (totalSamplesQueued * 1_000_000L) / sampleRate.toLong()
                activeCodec.queueInputBuffer(inputIndex, 0, size, ptsUs, 0)
                totalSamplesQueued += (size / 2).toLong()
                return
            }
            drainOutputLocked(activeCodec, endOfStream = false)
        }
    }

    private fun queueEndOfStreamLocked(activeCodec: MediaCodec) {
        val deadline = System.currentTimeMillis() + 300L
        while (System.currentTimeMillis() < deadline) {
            val inputIndex = activeCodec.dequeueInputBuffer(15_000L)
            if (inputIndex >= 0) {
                val ptsUs = (totalSamplesQueued * 1_000_000L) / sampleRate.toLong()
                activeCodec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return
            }
            drainOutputLocked(activeCodec, endOfStream = false)
        }
    }

    private fun ensureContainerOpenLocked(): PureM4aContainerWriter? {
        m4aContainer?.let { return it }
        return try {
            val created = PureM4aContainerWriter(
                outputFile = outputFile,
                sampleRate = sampleRate,
                bitRate = bitRate
            )
            m4aContainer = created
            created
        } catch (_: Exception) {
            null
        }
    }

    private fun captureCsdFromFormat(format: MediaFormat?) {
        val csd = format?.getByteBuffer("csd-0") ?: return
        if (csd.remaining() <= 0) return
        val dup = csd.duplicate()
        val bytes = ByteArray(dup.remaining())
        dup.get(bytes)
        if (bytes.isNotEmpty()) {
            audioSpecificConfig = bytes
        }
    }

    private fun drainOutputLocked(activeCodec: MediaCodec, endOfStream: Boolean) {
        val deadlineMs = if (endOfStream) System.currentTimeMillis() + 800L else Long.MAX_VALUE
        var iterations = 0
        val maxNonEosIterations = 24

        while (true) {
            if (endOfStream && System.currentTimeMillis() >= deadlineMs) return
            if (!endOfStream && iterations++ >= maxNonEosIterations) return

            val timeoutUs = if (endOfStream) 15_000L else 0L
            val outputIndex = activeCodec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    runCatching { captureCsdFromFormat(activeCodec.outputFormat) }
                }
                outputIndex >= 0 -> {
                    val encodedData = activeCodec.getOutputBuffer(outputIndex)
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (encodedData != null && bufferInfo.size > 0) {
                        val frameBytes = ByteArray(bufferInfo.size)
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        encodedData.get(frameBytes)

                        if (isConfig) {
                            if (audioSpecificConfig == null && frameBytes.isNotEmpty()) {
                                audioSpecificConfig = frameBytes
                            }
                        } else {
                            if (audioSpecificConfig == null) {
                                runCatching { captureCsdFromFormat(activeCodec.outputFormat) }
                            }
                            ensureContainerOpenLocked()?.writeAacFrame(frameBytes)
                        }
                    }
                    activeCodec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return
                    }
                }
            }
        }
    }

    private fun releaseCodecAndContainerLocked(
        activeCodec: MediaCodec?,
        finalizeContainer: Boolean
    ) {
        if (activeCodec != null) {
            runCatching { activeCodec.stop() }
            runCatching { activeCodec.release() }
        }
        val container = m4aContainer
        m4aContainer = null
        if (container != null) {
            if (finalizeContainer) {
                runCatching { container.finishAndClose(audioSpecificConfig) }
            } else {
                runCatching { container.abortAndDelete() }
            }
        } else if (finalizeContainer) {
            runCatching { outputFile.delete() }
        }
    }
}

/**
 * Pure-Kotlin ISO BMFF (ISO/IEC 14496-12 / MP4) audio-only AAC-LC (.m4a) writer.
 * Produces standard `ftyp` + `mdat` + `moov` files playable by Android MediaPlayer,
 * ExoPlayer, and desktop media players without invoking native Stagefright MPEG4Writer.
 */
internal class PureM4aContainerWriter(
    private val outputFile: File,
    private val sampleRate: Int,
    private val bitRate: Int
) {
    private val raf = RandomAccessFile(outputFile, "rw")
    private val frameSizes = ArrayList<Int>(512)
    private val chunkOffsets = ArrayList<Int>(64)
    private var mdatBoxStartOffset = 0L
    private var mdatPayloadBytes = 0L
    private var closed = false

    init {
        raf.setLength(0L)
        val ftyp = buildFtypBox()
        raf.write(ftyp)
        mdatBoxStartOffset = ftyp.size.toLong()
        // Reserve 8-byte mdat box header (size patched during finishAndClose)
        raf.writeInt(8)
        raf.writeBytes("mdat")
    }

    fun writeAacFrame(frameBytes: ByteArray) {
        if (closed || frameBytes.isEmpty()) return
        val currentOffset = mdatBoxStartOffset + 8L + mdatPayloadBytes
        // Guard against 32-bit stco overflow (~2 GB, >70 hours of call audio)
        if (currentOffset + frameBytes.size.toLong() >= Int.MAX_VALUE.toLong() - 65_536L) return
        if (frameSizes.size % FRAMES_PER_CHUNK == 0) {
            chunkOffsets.add(currentOffset.toInt())
        }
        raf.write(frameBytes)
        frameSizes.add(frameBytes.size)
        mdatPayloadBytes += frameBytes.size.toLong()
    }

    fun finishAndClose(codecSpecificConfig: ByteArray?) {
        if (closed) return
        closed = true
        try {
            if (frameSizes.isEmpty() || mdatPayloadBytes <= 0L) {
                runCatching { raf.close() }
                runCatching { outputFile.delete() }
                return
            }
            val totalMdatSize = (8L + mdatPayloadBytes).toInt()
            raf.seek(mdatBoxStartOffset)
            raf.writeInt(totalMdatSize)

            val moovOffset = mdatBoxStartOffset + 8L + mdatPayloadBytes
            raf.seek(moovOffset)
            val asc = codecSpecificConfig?.takeIf { it.isNotEmpty() }
                ?: buildFallbackAudioSpecificConfig(sampleRate = sampleRate, channelCount = 1)
            raf.write(buildMoovBox(asc))
            raf.fd.sync()
        } catch (_: Exception) {
            runCatching { outputFile.delete() }
        } finally {
            runCatching { raf.close() }
        }
    }

    fun abortAndDelete() {
        if (!closed) {
            closed = true
            runCatching { raf.close() }
        }
        runCatching { outputFile.delete() }
    }

    private fun buildFtypBox(): ByteArray = mp4Box("ftyp") { out ->
        out.writeBytes("M4A ")
        out.writeInt(0)
        out.writeBytes("M4A ")
        out.writeBytes("mp42")
        out.writeBytes("isom")
    }

    private fun buildMoovBox(asc: ByteArray): ByteArray {
        val totalDurationSamples = (frameSizes.size.toLong() * AAC_LC_SAMPLES_PER_FRAME.toLong())
            .coerceAtMost(0x7FFF_FFFFL)
            .toInt()
        return mp4Box("moov") { out ->
            out.write(buildMvhdBox(totalDurationSamples))
            out.write(buildTrakBox(totalDurationSamples, asc))
        }
    }

    private fun buildMvhdBox(durationSamples: Int): ByteArray = mp4FullBox("mvhd", 0, 0) { out ->
        out.writeInt(0) // creation_time
        out.writeInt(0) // modification_time
        out.writeInt(sampleRate) // timescale
        out.writeInt(durationSamples) // duration
        out.writeInt(0x0001_0000) // rate = 1.0
        out.writeShort(0x0100) // volume = 1.0
        out.write(ByteArray(10)) // reserved
        writeIdentityMatrix(out)
        out.write(ByteArray(24)) // pre_defined
        out.writeInt(2) // next_track_ID
    }

    private fun buildTrakBox(durationSamples: Int, asc: ByteArray): ByteArray = mp4Box("trak") { out ->
        out.write(buildTkhdBox(durationSamples))
        out.write(buildMdiaBox(durationSamples, asc))
    }

    private fun buildTkhdBox(durationSamples: Int): ByteArray = mp4FullBox("tkhd", 0, 0x000003) { out ->
        out.writeInt(0) // creation_time
        out.writeInt(0) // modification_time
        out.writeInt(1) // track_ID
        out.writeInt(0) // reserved
        out.writeInt(durationSamples) // duration
        out.writeInt(0) // reserved[0]
        out.writeInt(0) // reserved[1]
        out.writeShort(0) // layer
        out.writeShort(0) // alternate_group
        out.writeShort(0x0100) // volume = 1.0 for audio track
        out.writeShort(0) // reserved
        writeIdentityMatrix(out)
        out.writeInt(0) // width
        out.writeInt(0) // height
    }

    private fun buildMdiaBox(durationSamples: Int, asc: ByteArray): ByteArray = mp4Box("mdia") { out ->
        out.write(buildMdhdBox(durationSamples))
        out.write(buildHdlrBox())
        out.write(buildMinfBox(asc))
    }

    private fun buildMdhdBox(durationSamples: Int): ByteArray = mp4FullBox("mdhd", 0, 0) { out ->
        out.writeInt(0) // creation_time
        out.writeInt(0) // modification_time
        out.writeInt(sampleRate) // timescale
        out.writeInt(durationSamples) // duration
        out.writeShort(0x55C4) // language = "und"
        out.writeShort(0) // pre_defined
    }

    private fun buildHdlrBox(): ByteArray = mp4FullBox("hdlr", 0, 0) { out ->
        out.writeInt(0) // pre_defined
        out.writeBytes("soun") // handler_type
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(0)
        out.writeBytes("SoundHandler")
        out.writeByte(0) // null terminator
    }

    private fun buildMinfBox(asc: ByteArray): ByteArray = mp4Box("minf") { out ->
        out.write(buildSmhdBox())
        out.write(buildDinfBox())
        out.write(buildStblBox(asc))
    }

    private fun buildSmhdBox(): ByteArray = mp4FullBox("smhd", 0, 0) { out ->
        out.writeShort(0) // balance
        out.writeShort(0) // reserved
    }

    private fun buildDinfBox(): ByteArray = mp4Box("dinf") { out ->
        val urlBox = mp4FullBox("url ", 0, 0x000001) { }
        val drefBox = mp4FullBox("dref", 0, 0) { drefOut ->
            drefOut.writeInt(1)
            drefOut.write(urlBox)
        }
        out.write(drefBox)
    }

    private fun buildStblBox(asc: ByteArray): ByteArray = mp4Box("stbl") { out ->
        out.write(buildStsdBox(asc))
        out.write(buildSttsBox())
        out.write(buildStscBox())
        out.write(buildStszBox())
        out.write(buildStcoBox())
    }

    private fun buildStsdBox(asc: ByteArray): ByteArray = mp4FullBox("stsd", 0, 0) { out ->
        out.writeInt(1) // entry_count
        val mp4a = mp4Box("mp4a") { mp4aOut ->
            mp4aOut.write(ByteArray(6)) // reserved
            mp4aOut.writeShort(1) // data_reference_index
            mp4aOut.writeInt(0) // reserved[0]
            mp4aOut.writeInt(0) // reserved[1]
            mp4aOut.writeShort(1) // channelcount = 1 (mono)
            mp4aOut.writeShort(16) // samplesize = 16-bit
            mp4aOut.writeShort(0) // pre_defined
            mp4aOut.writeShort(0) // reserved
            mp4aOut.writeInt(sampleRate shl 16) // 16.16 fixed-point sample rate
            mp4aOut.write(buildEsdsBox(asc))
        }
        out.write(mp4a)
    }

    private fun buildEsdsBox(asc: ByteArray): ByteArray = mp4FullBox("esds", 0, 0) { out ->
        val dsi = buildDescriptor(0x05) { d -> d.write(asc) }
        val dcd = buildDescriptor(0x04) { d ->
            d.writeByte(0x40) // MPEG-4 Audio
            d.writeByte(0x15) // AudioStream (5 << 2) | 1
            d.writeByte(0x00) // bufferSizeDB (24-bit = 1536)
            d.writeByte(0x06)
            d.writeByte(0x00)
            d.writeInt(bitRate) // maxBitrate
            d.writeInt(bitRate) // avgBitrate
            d.write(dsi)
        }
        val slc = buildDescriptor(0x06) { d ->
            d.writeByte(0x02) // predefined = MP4
        }
        val esd = buildDescriptor(0x03) { d ->
            d.writeShort(1) // ES_ID
            d.writeByte(0x00) // flags
            d.write(dcd)
            d.write(slc)
        }
        out.write(esd)
    }

    private fun buildSttsBox(): ByteArray = mp4FullBox("stts", 0, 0) { out ->
        out.writeInt(1) // entry_count
        out.writeInt(frameSizes.size) // sample_count
        out.writeInt(AAC_LC_SAMPLES_PER_FRAME) // sample_delta
    }

    private fun buildStscBox(): ByteArray = mp4FullBox("stsc", 0, 0) { out ->
        val totalFrames = frameSizes.size
        val remainder = totalFrames % FRAMES_PER_CHUNK
        if (totalFrames <= FRAMES_PER_CHUNK) {
            out.writeInt(1)
            out.writeInt(1)
            out.writeInt(totalFrames)
            out.writeInt(1)
        } else if (remainder == 0) {
            out.writeInt(1)
            out.writeInt(1)
            out.writeInt(FRAMES_PER_CHUNK)
            out.writeInt(1)
        } else {
            out.writeInt(2)
            out.writeInt(1)
            out.writeInt(FRAMES_PER_CHUNK)
            out.writeInt(1)
            out.writeInt(chunkOffsets.size)
            out.writeInt(remainder)
            out.writeInt(1)
        }
    }

    private fun buildStszBox(): ByteArray = mp4FullBox("stsz", 0, 0) { out ->
        out.writeInt(0) // variable sample size
        out.writeInt(frameSizes.size)
        for (sz in frameSizes) {
            out.writeInt(sz)
        }
    }

    private fun buildStcoBox(): ByteArray = mp4FullBox("stco", 0, 0) { out ->
        out.writeInt(chunkOffsets.size)
        for (offset in chunkOffsets) {
            out.writeInt(offset)
        }
    }

    private fun writeIdentityMatrix(out: DataOutputStream) {
        out.writeInt(0x0001_0000)
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(0x0001_0000)
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(0)
        out.writeInt(0x4000_0000)
    }

    private fun mp4Box(type: String, writer: (DataOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { writer(it) }
        val payload = body.toByteArray()
        val box = ByteArrayOutputStream(payload.size + 8)
        DataOutputStream(box).use { out ->
            out.writeInt(payload.size + 8)
            out.writeBytes(type)
            out.write(payload)
        }
        return box.toByteArray()
    }

    private fun mp4FullBox(
        type: String,
        version: Int,
        flags: Int,
        writer: (DataOutputStream) -> Unit
    ): ByteArray = mp4Box(type) { out ->
        out.writeInt(((version and 0xFF) shl 24) or (flags and 0x00FF_FFFF))
        writer(out)
    }

    private fun buildDescriptor(tag: Int, writer: (DataOutputStream) -> Unit): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { writer(it) }
        val payload = body.toByteArray()
        val out = ByteArrayOutputStream(payload.size + 5)
        out.write(tag)
        var remaining = payload.size
        val lenBytes = ByteArray(4)
        var count = 0
        do {
            lenBytes[count++] = (remaining and 0x7F).toByte()
            remaining = remaining ushr 7
        } while (remaining > 0 && count < 4)
        for (i in count - 1 downTo 0) {
            val b = lenBytes[i].toInt() or (if (i > 0) 0x80 else 0)
            out.write(b)
        }
        out.write(payload)
        return out.toByteArray()
    }

    companion object {
        private const val AAC_LC_SAMPLES_PER_FRAME = 1024
        private const val FRAMES_PER_CHUNK = 64

        internal fun buildFallbackAudioSpecificConfig(
            sampleRate: Int,
            channelCount: Int = 1
        ): ByteArray {
            val freqIndex = when (sampleRate) {
                96_000 -> 0
                88_200 -> 1
                64_000 -> 2
                48_000 -> 3
                44_100 -> 4
                32_000 -> 5
                24_000 -> 6
                22_050 -> 7
                16_000 -> 8
                12_000 -> 9
                11_025 -> 10
                8_000 -> 11
                7_350 -> 12
                else -> 8
            }
            val objectType = 2 // AAC-LC
            val byte0 = ((objectType shl 3) or (freqIndex ushr 1)).toByte()
            val byte1 = (((freqIndex and 1) shl 7) or (channelCount shl 3)).toByte()
            return byteArrayOf(byte0, byte1)
        }
    }
}

/**
 * Thread-safe uncompressed RIFF PCM-16 mono WAV writer used as a zero-failure fallback
 * if MediaCodec AAC is unavailable on an unusual device/emulator.
 */
internal class WavPcmWriter(
    override val outputFile: File,
    private val sampleRate: Int
) : PcmAudioFileWriter {

    override val fileExtension: String = "wav"

    private val lock = Any()
    private val raf = RandomAccessFile(outputFile, "rw")
    private var totalDataBytes = 0L
    private var closed = false

    init {
        raf.setLength(0L)
        // Reserve 44-byte WAV header
        raf.write(ByteArray(44))
    }

    override fun writePcm(samples: ShortArray, count: Int) {
        if (count <= 0) return
        synchronized(lock) {
            if (closed) return
            val bytes = ByteArray(count * 2)
            var offset = 0
            for (i in 0 until count) {
                val s = samples[i].toInt()
                bytes[offset++] = (s and 0xFF).toByte()
                bytes[offset++] = ((s shr 8) and 0xFF).toByte()
            }
            raf.write(bytes)
            totalDataBytes += bytes.size.toLong()
        }
    }

    override fun finishAndClose() {
        synchronized(lock) {
            if (closed) return
            closed = true
            try {
                writeWavHeaderLocked()
            } catch (_: Exception) {
            } finally {
                runCatching { raf.close() }
            }
            if (totalDataBytes <= 0L) {
                runCatching { outputFile.delete() }
            }
        }
    }

    override fun abortAndDelete() {
        synchronized(lock) {
            if (!closed) {
                closed = true
                runCatching { raf.close() }
            }
            runCatching { outputFile.delete() }
        }
    }

    private fun writeWavHeaderLocked() {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffChunkSize = (36L + totalDataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val dataChunkSize = totalDataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        putIntLe(header, 4, riffChunkSize)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        putIntLe(header, 16, 16)
        putShortLe(header, 20, 1)
        putShortLe(header, 22, channels)
        putIntLe(header, 24, sampleRate)
        putIntLe(header, 28, byteRate)
        putShortLe(header, 32, blockAlign)
        putShortLe(header, 34, bitsPerSample)
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        putIntLe(header, 40, dataChunkSize)

        raf.seek(0L)
        raf.write(header)
    }

    private fun putIntLe(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun putShortLe(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
