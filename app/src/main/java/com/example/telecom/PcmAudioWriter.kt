package com.example.telecom

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
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
 * Hardware/software AAC-LC encoder writing standards-compliant MPEG-4 (.m4a) files
 * using Android's MediaCodec + MediaMuxer.
 */
internal class AacM4aPcmWriter(
    override val outputFile: File,
    private val sampleRate: Int,
    bitRate: Int = if (sampleRate <= 16_000) 64_000 else 96_000
) : PcmAudioFileWriter {

    override val fileExtension: String = "m4a"

    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private val bufferInfo = MediaCodec.BufferInfo()
    private var trackIndex = -1
    private var muxerStarted = false
    private var totalSamplesQueued = 0L
    private var lastMuxedPtsUs = -1L
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
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            runCatching { createdCodec.stop() }
            runCatching { createdCodec.release() }
            throw e
        }
    }

    override fun writePcm(samples: ShortArray, count: Int) {
        if (closed || count <= 0) return
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
            val inputIndex = codec.dequeueInputBuffer(10_000L)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: break
                inputBuffer.clear()
                val chunkBytes = min(inputBuffer.remaining(), totalBytes - writtenBytes)
                inputBuffer.put(pcmBytes, writtenBytes, chunkBytes)
                val ptsUs = (totalSamplesQueued * 1_000_000L) / sampleRate.toLong()
                codec.queueInputBuffer(inputIndex, 0, chunkBytes, ptsUs, 0)
                totalSamplesQueued += (chunkBytes / 2).toLong()
                writtenBytes += chunkBytes
            }
            drainOutput(endOfStream = false)
        }
    }

    override fun finishAndClose() {
        if (closed) return
        closed = true
        try {
            val inputIndex = codec.dequeueInputBuffer(50_000L)
            if (inputIndex >= 0) {
                val ptsUs = (totalSamplesQueued * 1_000_000L) / sampleRate.toLong()
                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
            drainOutput(endOfStream = true)
        } catch (_: Exception) {
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
    }

    override fun abortAndDelete() {
        if (!closed) {
            closed = true
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) {
                runCatching { muxer.stop() }
            }
            runCatching { muxer.release() }
        }
        runCatching { outputFile.delete() }
    }

    private fun drainOutput(endOfStream: Boolean) {
        val maxIterations = if (endOfStream) 80 else 16
        val timeoutUs = if (endOfStream) 15_000L else 0L
        for (i in 0 until maxIterations) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outputIndex >= 0 -> {
                    val encodedData = codec.getOutputBuffer(outputIndex)
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (encodedData != null && !isConfig && bufferInfo.size > 0 && muxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        if (bufferInfo.presentationTimeUs <= lastMuxedPtsUs) {
                            bufferInfo.presentationTimeUs = lastMuxedPtsUs + 1L
                        }
                        lastMuxedPtsUs = bufferInfo.presentationTimeUs
                        muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return
                    }
                }
            }
        }
    }
}

/**
 * Uncompressed RIFF PCM-16 mono WAV writer used as a zero-failure fallback if MediaCodec AAC
 * is unavailable on an unusual device/emulator.
 */
internal class WavPcmWriter(
    override val outputFile: File,
    private val sampleRate: Int
) : PcmAudioFileWriter {

    override val fileExtension: String = "wav"

    private val raf = RandomAccessFile(outputFile, "rw")
    private var totalDataBytes = 0L
    private var closed = false

    init {
        raf.setLength(0L)
        // Reserve 44-byte WAV header
        raf.write(ByteArray(44))
    }

    override fun writePcm(samples: ShortArray, count: Int) {
        if (closed || count <= 0) return
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

    override fun finishAndClose() {
        if (closed) return
        closed = true
        try {
            writeWavHeader()
        } catch (_: Exception) {
        } finally {
            runCatching { raf.close() }
        }
    }

    override fun abortAndDelete() {
        if (!closed) {
            closed = true
            runCatching { raf.close() }
        }
        runCatching { outputFile.delete() }
    }

    private fun writeWavHeader() {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val riffChunkSize = (36L + totalDataBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val dataChunkSize = totalDataBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        val header = ByteArray(44)
        // "RIFF"
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        putIntLe(header, 4, riffChunkSize)
        // "WAVE"
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        // "fmt "
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        putIntLe(header, 16, 16) // PCM fmt chunk size
        putShortLe(header, 20, 1) // AudioFormat = PCM
        putShortLe(header, 22, channels)
        putIntLe(header, 24, sampleRate)
        putIntLe(header, 28, byteRate)
        putShortLe(header, 32, blockAlign)
        putShortLe(header, 34, bitsPerSample)
        // "data"
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
