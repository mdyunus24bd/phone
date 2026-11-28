package com.example.telecom

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

data class FinishedRecording(
    val fileName: String,
    val storageUri: String,
    val storagePath: String,
    val durationSec: Long,
    val fileSizeBytes: Long,
    val encryptedAtRest: Boolean,
    val audioSilencedAllCall: Boolean = false
)

sealed class CallRecordingState {
    data object Idle : CallRecordingState()
    data object Starting : CallRecordingState()
    data class Recording(
        val startedAtMs: Long,
        val callerLabel: String,
        val networkType: String,
        val isAutoTriggered: Boolean,
        val audioSourceLabel: String,
        val isSilencedBySystem: Boolean = false
    ) : CallRecordingState()
    data object Saving : CallRecordingState()
    data class Error(val message: String) : CallRecordingState()
}

private data class AudioSourceCandidate(
    val source: Int,
    val label: String,
    val preferredRates: List<Int>
)

private data class OpenedCaptureSession(
    val audioRecord: AudioRecord,
    val source: Int,
    val sourceLabel: String,
    val sampleRate: Int,
    val bufferSizeShorts: Int,
    val preCapturedChunks: List<ShortArray>,
    val isSilenced: Boolean,
    val hasNonZeroSignal: Boolean,
    val recordingCallback: AudioManager.AudioRecordingCallback? = null
)

/**
 * Real-time CallRecordingManager powered by Android's AudioRecord + MediaCodec (AAC .m4a)
 * with WAV fallback, microphone Foreground Service readiness synchronization, active
 * multi-source probing, Android 10+ `isClientSilenced` detection, and adaptive two-way
 * voice gain control (AGC).
 */
class CallRecordingManager(
    private val context: Context,
    private val cryptoVault: CryptoVault
) {
    private val recorderMutex = Mutex()
    private val captureRunning = AtomicBoolean(false)
    private val discardOnStop = AtomicBoolean(false)
    private val capturedNonZeroAudio = AtomicBoolean(false)
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var activeAudioRecord: AudioRecord? = null
    @Volatile
    private var activeRecordingCallback: AudioManager.AudioRecordingCallback? = null
    @Volatile
    private var activeWriter: PcmAudioFileWriter? = null
    @Volatile
    private var captureThread: Thread? = null

    private var activeCaptureFile: File? = null
    private var activeFileExtension: String = "m4a"
    private var startedAtMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activeTempPlaybackFile: File? = null

    private val _recordingState = MutableStateFlow<CallRecordingState>(CallRecordingState.Idle)
    val recordingState: StateFlow<CallRecordingState> = _recordingState.asStateFlow()

    private val _liveAudioLevel = MutableStateFlow(0f)
    val liveAudioLevel: StateFlow<Float> = _liveAudioLevel.asStateFlow()

    private val _isAudioSilenced = MutableStateFlow(false)
    val isAudioSilenced: StateFlow<Boolean> = _isAudioSilenced.asStateFlow()

    val isRecording: Boolean
        get() = _recordingState.value is CallRecordingState.Recording

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Private app internal storage directory where call recordings are persisted.
     */
    fun internalRecordingsDir(): File {
        return File(context.filesDir, INTERNAL_RECORDINGS_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun legacyEncryptedDir(): File {
        return File(context.filesDir, "encrypted_recordings").apply {
            if (!exists()) mkdirs()
        }
    }

    fun internalStorageFolderLabel(): String = "Internal Storage / $INTERNAL_RECORDINGS_DIR"

    /**
     * Starts call recording after waiting for the microphone Foreground Service to be active,
     * then probes Android audio sources to pick one that delivers real non-silenced audio
     * (never silently sticking on a zero-filled VOICE_COMMUNICATION stream).
     */
    suspend fun startBackgroundRecording(
        callerLabel: String,
        networkType: String,
        isAutoTriggered: Boolean = false
    ): Result<String> = recorderMutex.withLock {
        if (!hasRecordAudioPermission()) {
            val err = SecurityException("RECORD_AUDIO permission not granted")
            _recordingState.value = CallRecordingState.Error(err.message ?: "Microphone permission required")
            return@withLock Result.failure(err)
        }

        _recordingState.value = CallRecordingState.Starting
        _liveAudioLevel.value = 0f
        _isAudioSilenced.value = false
        capturedNonZeroAudio.set(false)

        withContext(Dispatchers.IO) {
            stopCaptureResourcesLocked(discardActiveFile = true)

            // Wait for telephony audio routing to settle when auto-triggered right on call connect
            if (isAutoTriggered) {
                delay(450L)
            }

            // Ensure microphone Foreground Service is promoted BEFORE opening AudioRecord
            // (prevents Android 11–15 from silencing capture due to FGS startup race)
            CallRecordingForegroundService.startAndAwaitReady(
                context = context,
                callerLabel = callerLabel,
                networkType = networkType,
                timeoutMs = 1500L
            )

            val session = probeAndOpenBestAudioSource()
            if (session == null) {
                CallRecordingForegroundService.stop(context)
                val failure = IllegalStateException("No compatible audio input source available")
                _recordingState.value = CallRecordingState.Error(
                    failure.localizedMessage ?: "Audio device unavailable"
                )
                return@withContext Result.failure(failure)
            }

            val recordingsDir = internalRecordingsDir()
            val timestamp = System.currentTimeMillis()
            val writer: PcmAudioFileWriter = try {
                val m4aFile = File(recordingsDir, "active_capture_$timestamp.m4a")
                AacM4aPcmWriter(outputFile = m4aFile, sampleRate = session.sampleRate)
            } catch (_: Exception) {
                val wavFile = File(recordingsDir, "active_capture_$timestamp.wav")
                WavPcmWriter(outputFile = wavFile, sampleRate = session.sampleRate)
            }

            activeAudioRecord = session.audioRecord
            activeRecordingCallback = session.recordingCallback
            activeWriter = writer
            activeCaptureFile = writer.outputFile
            activeFileExtension = writer.fileExtension
            startedAtMs = System.currentTimeMillis()
            _isAudioSilenced.value = session.isSilenced
            if (session.hasNonZeroSignal) {
                capturedNonZeroAudio.set(true)
            }

            // Adaptive gain boosts quiet far-end earpiece/speaker pickup on mic/voice-recog sources
            val gainProcessor = AdaptiveCallVoiceGain(
                maxGain = if (session.source == MediaRecorder.AudioSource.VOICE_CALL) 1.8f else 4.5f,
                targetPeak = 18_000f,
                noiseGatePeak = 20
            )

            discardOnStop.set(false)
            captureRunning.set(true)
            val worker = Thread({
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                runCaptureLoop(
                    audioRecord = session.audioRecord,
                    writer = writer,
                    sampleRate = session.sampleRate,
                    bufferSizeShorts = session.bufferSizeShorts,
                    preCapturedChunks = session.preCapturedChunks,
                    gainProcessor = gainProcessor
                )
            }, "VoxLineCallAudioCapture")

            captureThread = worker
            worker.start()

            _recordingState.value = CallRecordingState.Recording(
                startedAtMs = startedAtMs,
                callerLabel = callerLabel,
                networkType = networkType,
                isAutoTriggered = isAutoTriggered,
                audioSourceLabel = session.sourceLabel,
                isSilencedBySystem = session.isSilenced
            )

            Result.success(session.sourceLabel)
        }
    }

    /**
     * Tests candidate Android audio sources in priority order using non-blocking short probes.
     * Immediately selects the first source that is NOT silenced by Android AudioPolicyService
     * and delivers real non-zero PCM audio. If all probes are quiet during the brief window,
     * always prefers an unsilenced source over a silenced source.
     */
    @SuppressLint("MissingPermission")
    private fun probeAndOpenBestAudioSource(): OpenedCaptureSession? {
        val candidates = buildCandidateSources()

        var bestUnsilencedFallback: Triple<Int, String, Int>? = null
        var bestSilencedFallback: Triple<Int, String, Int>? = null

        for (candidate in candidates) {
            for (rate in candidate.preferredRates) {
                val probed = tryProbeAudioSource(
                    source = candidate.source,
                    label = candidate.label,
                    sampleRate = rate
                ) ?: continue

                // If Android did NOT silence this source AND we got real non-zero PCM, lock onto it!
                if (!probed.isSilenced && probed.hasNonZeroSignal) {
                    return probed
                }

                // Always prefer an unsilenced source over a silenced source for fallback
                if (!probed.isSilenced) {
                    if (bestUnsilencedFallback == null) {
                        bestUnsilencedFallback = Triple(candidate.source, candidate.label, rate)
                    }
                } else if (bestSilencedFallback == null) {
                    bestSilencedFallback = Triple(candidate.source, candidate.label, rate)
                }

                releaseProbedSession(probed)
                break // No need to test lower sample rates for the same source once initialized
            }
        }

        val fallback = bestUnsilencedFallback ?: bestSilencedFallback ?: return null
        return openAudioRecordDirect(
            source = fallback.first,
            label = fallback.second,
            sampleRate = fallback.third
        )
    }

    private fun buildCandidateSources(): List<AudioSourceCandidate> {
        val list = mutableListOf<AudioSourceCandidate>()
        // 1. Direct telephony bus (works natively on OEM ROMs that expose it without Accessibility)
        list += AudioSourceCandidate(
            source = MediaRecorder.AudioSource.VOICE_CALL,
            label = "VOICE_CALL",
            preferredRates = listOf(16_000, 8_000, 44_100, 48_000)
        )
        // 2. VOICE_RECOGNITION: avoids aggressive VoIP echo cancellation that attenuates far-end voice
        list += AudioSourceCandidate(
            source = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            label = "VOICE_RECOG",
            preferredRates = listOf(16_000, 44_100, 48_000, 8_000)
        )
        // 3. Raw MIC with setPrivacySensitive(false) often succeeds on OEMs that allow mic capture
        list += AudioSourceCandidate(
            source = MediaRecorder.AudioSource.MIC,
            label = "MIC",
            preferredRates = listOf(44_100, 16_000, 48_000)
        )
        // 4. VOICE_COMMUNICATION: VoIP/call mic route permitted by some OEM HALs during active calls
        list += AudioSourceCandidate(
            source = MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            label = "VOICE_COMM",
            preferredRates = listOf(16_000, 44_100, 48_000)
        )
        // 5. UNPROCESSED: non-privacy-sensitive raw capture path on Android 7+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list += AudioSourceCandidate(
                source = MediaRecorder.AudioSource.UNPROCESSED,
                label = "UNPROCESSED",
                preferredRates = listOf(44_100, 16_000, 48_000)
            )
        }
        return list
    }

    @SuppressLint("MissingPermission")
    private fun createConfiguredAudioRecord(
        source: Int,
        sampleRate: Int
    ): Pair<AudioRecord, Int>? {
        val minBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBytes <= 0) return null

        val bufferBytes = maxOf(minBytes * 3, sampleRate / 5 * 2) // >= 200ms PCM16 mono
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val isTelephonyBusSource = source == MediaRecorder.AudioSource.VOICE_CALL ||
            source == MediaRecorder.AudioSource.VOICE_UPLINK ||
            source == MediaRecorder.AudioSource.VOICE_DOWNLINK

        fun buildRecord(optOutPrivacySensitive: Boolean): AudioRecord? {
            val builder = AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)

            // Android 11+: opt out of privacy-sensitive exclusivity on mic sources so we do not
            // mark ourselves exclusive or get auto-silenced unnecessarily. Avoid setting this
            // flag on VOICE_CALL where OEM HALs can reject it.
            if (optOutPrivacySensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { builder.setPrivacySensitive(false) }
            }

            val rec = try {
                builder.build()
            } catch (_: Throwable) {
                return null
            }

            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                releaseAudioRecordSafe(rec)
                return null
            }
            return rec
        }

        val record = buildRecord(optOutPrivacySensitive = !isTelephonyBusSource)
            ?: (if (!isTelephonyBusSource) buildRecord(optOutPrivacySensitive = false) else null)
            ?: return null

        // Hint telephony input device on VOICE_CALL when OEM exposes TYPE_TELEPHONY
        if (isTelephonyBusSource && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val telephonyIn = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
                    ?.firstOrNull { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
                if (telephonyIn != null) {
                    record.preferredDevice = telephonyIn
                }
            }
        }

        val readChunkShorts = (sampleRate / 25).coerceIn(320, 2048) // ~40ms frames
        return record to readChunkShorts
    }

    private fun tryProbeAudioSource(
        source: Int,
        label: String,
        sampleRate: Int
    ): OpenedCaptureSession? {
        val (record, chunkShorts) = createConfiguredAudioRecord(source, sampleRate) ?: return null
        val callback = createAndRegisterSilencedMonitor(record)
        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                unregisterCallbackSafe(record, callback)
                releaseAudioRecordSafe(record)
                return null
            }

            val probeChunks = mutableListOf<ShortArray>()
            val tempBuf = ShortArray(chunkShorts)
            var peakAmplitude = 0
            var silencedByApi = false
            val deadlineMs = System.currentTimeMillis() + 360L

            while (System.currentTimeMillis() < deadlineMs) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (record.activeRecordingConfiguration?.isClientSilenced == true) {
                        silencedByApi = true
                        break
                    }
                }
                val read = record.read(tempBuf, 0, tempBuf.size, AudioRecord.READ_NON_BLOCKING)
                if (read > 0) {
                    val copy = tempBuf.copyOf(read)
                    probeChunks.add(copy)
                    for (i in 0 until read) {
                        val a = abs(copy[i].toInt())
                        if (a > peakAmplitude) peakAmplitude = a
                    }
                    if (peakAmplitude > 0) {
                        // Confirmed live non-zero audio from this source
                        break
                    }
                } else if (read < 0) {
                    unregisterCallbackSafe(record, callback)
                    releaseAudioRecordSafe(record)
                    return null
                } else {
                    Thread.sleep(20L)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !silencedByApi) {
                silencedByApi = record.activeRecordingConfiguration?.isClientSilenced == true
            }

            OpenedCaptureSession(
                audioRecord = record,
                source = source,
                sourceLabel = label,
                sampleRate = sampleRate,
                bufferSizeShorts = chunkShorts,
                preCapturedChunks = probeChunks,
                isSilenced = silencedByApi,
                hasNonZeroSignal = peakAmplitude > 0,
                recordingCallback = callback
            )
        } catch (_: Throwable) {
            unregisterCallbackSafe(record, callback)
            releaseAudioRecordSafe(record)
            null
        }
    }

    private fun openAudioRecordDirect(
        source: Int,
        label: String,
        sampleRate: Int
    ): OpenedCaptureSession? {
        val (record, chunkShorts) = createConfiguredAudioRecord(source, sampleRate) ?: return null
        val callback = createAndRegisterSilencedMonitor(record)
        return try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                unregisterCallbackSafe(record, callback)
                releaseAudioRecordSafe(record)
                return null
            }
            val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                record.activeRecordingConfiguration?.isClientSilenced == true
            } else {
                false
            }
            OpenedCaptureSession(
                audioRecord = record,
                source = source,
                sourceLabel = label,
                sampleRate = sampleRate,
                bufferSizeShorts = chunkShorts,
                preCapturedChunks = emptyList(),
                isSilenced = silenced,
                hasNonZeroSignal = false,
                recordingCallback = callback
            )
        } catch (_: Throwable) {
            unregisterCallbackSafe(record, callback)
            releaseAudioRecordSafe(record)
            null
        }
    }

    /**
     * Registers [AudioManager.AudioRecordingCallback] BEFORE calling [AudioRecord.startRecording],
     * as required by Android's AudioRecord specification so live silenced/unsilenced transitions
     * are delivered reliably.
     */
    private fun createAndRegisterSilencedMonitor(audioRecord: AudioRecord): AudioManager.AudioRecordingCallback? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val sessionId = audioRecord.audioSessionId
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
                if (activeAudioRecord !== audioRecord) return
                val mine = configs?.firstOrNull { it.clientAudioSessionId == sessionId } ?: return
                updateSilencedFlag(mine.isClientSilenced)
            }
        }
        return runCatching {
            audioRecord.registerAudioRecordingCallback(callbackExecutor, callback)
            callback
        }.getOrNull()
    }

    private fun releaseProbedSession(session: OpenedCaptureSession) {
        unregisterCallbackSafe(session.audioRecord, session.recordingCallback)
        releaseAudioRecordSafe(session.audioRecord)
    }

    private fun unregisterCallbackSafe(
        record: AudioRecord,
        callback: AudioManager.AudioRecordingCallback?
    ) {
        if (callback == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching { record.unregisterAudioRecordingCallback(callback) }
    }

    private fun updateSilencedFlag(silenced: Boolean) {
        if (_isAudioSilenced.value == silenced) return
        _isAudioSilenced.value = silenced
        val current = _recordingState.value
        if (current is CallRecordingState.Recording) {
            _recordingState.value = current.copy(isSilencedBySystem = silenced)
        }
    }

    private fun runCaptureLoop(
        audioRecord: AudioRecord,
        writer: PcmAudioFileWriter,
        sampleRate: Int,
        bufferSizeShorts: Int,
        preCapturedChunks: List<ShortArray>,
        gainProcessor: AdaptiveCallVoiceGain
    ) {
        var consecutiveZeroSamples = 0L
        val silenceDetectThresholdSamples = sampleRate * 2L // 2.0 seconds of strictly all-zero PCM
        var lastMeterUpdateMs = 0L

        try {
            // Write any audio captured during the probe window so no speech is clipped at the start
            for (chunk in preCapturedChunks) {
                val level = gainProcessor.processInPlace(chunk, chunk.size)
                writer.writePcm(chunk, chunk.size)
                if (level > 0f) {
                    _liveAudioLevel.value = level
                    consecutiveZeroSamples = 0L
                }
            }

            val readBuffer = ShortArray(bufferSizeShorts)
            while (captureRunning.get()) {
                val readCount = audioRecord.read(
                    readBuffer,
                    0,
                    readBuffer.size,
                    AudioRecord.READ_BLOCKING
                )
                if (readCount <= 0) {
                    if (!captureRunning.get()) break
                    if (readCount == AudioRecord.ERROR_DEAD_OBJECT ||
                        readCount == AudioRecord.ERROR_INVALID_OPERATION
                    ) {
                        break
                    }
                    continue
                }

                var rawPeak = 0
                for (i in 0 until readCount) {
                    val a = abs(readBuffer[i].toInt())
                    if (a > rawPeak) rawPeak = a
                }

                val isApiSilenced = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    audioRecord.activeRecordingConfiguration?.isClientSilenced == true

                if (rawPeak == 0) {
                    consecutiveZeroSamples += readCount.toLong()
                    if (isApiSilenced || consecutiveZeroSamples >= silenceDetectThresholdSamples) {
                        updateSilencedFlag(true)
                    }
                } else {
                    capturedNonZeroAudio.set(true)
                    consecutiveZeroSamples = 0L
                    if (!isApiSilenced) {
                        updateSilencedFlag(false)
                    }
                }

                val level = gainProcessor.processInPlace(readBuffer, readCount)
                writer.writePcm(readBuffer, readCount)

                val now = System.currentTimeMillis()
                if (now - lastMeterUpdateMs >= 110L) {
                    lastMeterUpdateMs = now
                    _liveAudioLevel.value = if (rawPeak == 0) 0f else level
                }
            }
        } catch (_: Throwable) {
        } finally {
            _liveAudioLevel.value = 0f
            if (discardOnStop.get()) {
                runCatching { writer.abortAndDelete() }
            } else {
                runCatching { writer.finishAndClose() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activeRecordingCallback?.let { cb ->
                    runCatching { audioRecord.unregisterAudioRecordingCallback(cb) }
                }
            }
            activeRecordingCallback = null
            releaseAudioRecordSafe(audioRecord)
        }
    }

    /**
     * Stops the active capture thread cleanly, flushes the AAC/WAV file, stops the foreground
     * service, and saves the finalized audio recording into the app's internal storage.
     */
    suspend fun stopAndSaveRecording(
        phoneNumber: String,
        networkType: String
    ): Result<FinishedRecording> = recorderMutex.withLock {
        withContext(Dispatchers.IO) {
            val rawFile = activeCaptureFile
            val ext = activeFileExtension.ifBlank { "m4a" }
            if (rawFile == null) {
                CallRecordingForegroundService.stop(context)
                _recordingState.value = CallRecordingState.Idle
                _liveAudioLevel.value = 0f
                _isAudioSilenced.value = false
                return@withContext Result.failure(IllegalStateException("No active call recording"))
            }

            _recordingState.value = CallRecordingState.Saving
            val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
            val silencedAllCall = !capturedNonZeroAudio.get()
            stopCaptureResourcesLocked(discardActiveFile = false)
            CallRecordingForegroundService.stop(context)

            try {
                if (!rawFile.exists() || rawFile.length() == 0L) {
                    runCatching { rawFile.delete() }
                    _recordingState.value = CallRecordingState.Idle
                    return@withContext Result.failure(IllegalStateException("Captured audio was empty"))
                }

                val safePeer = phoneNumber
                    .replace(Regex("[^A-Za-z0-9+]"), "_")
                    .trim('_')
                    .take(18)
                    .ifBlank { "Unknown" }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "VoxLine_${networkType}_${safePeer}_${timestamp}.$ext"
                val destFile = File(internalRecordingsDir(), fileName)

                val finalFile = if (rawFile.renameTo(destFile)) {
                    destFile
                } else {
                    rawFile.copyTo(destFile, overwrite = true)
                    runCatching { rawFile.delete() }
                    destFile
                }

                val savedSize = finalFile.length()
                if (savedSize <= 0L) {
                    runCatching { finalFile.delete() }
                    _recordingState.value = CallRecordingState.Idle
                    return@withContext Result.failure(IllegalStateException("Saved audio file was empty"))
                }

                _recordingState.value = CallRecordingState.Idle
                Result.success(
                    FinishedRecording(
                        fileName = fileName,
                        storageUri = Uri.fromFile(finalFile).toString(),
                        storagePath = "Internal Storage/$INTERNAL_RECORDINGS_DIR/$fileName",
                        durationSec = durationSec,
                        fileSizeBytes = savedSize,
                        encryptedAtRest = false,
                        audioSilencedAllCall = silencedAllCall
                    )
                )
            } catch (e: Exception) {
                runCatching { rawFile.delete() }
                _recordingState.value = CallRecordingState.Idle
                Result.failure(e)
            }
        }
    }

    /**
     * Cancels and discards any active recording without keeping an internal file.
     */
    suspend fun cancelRecording() = recorderMutex.withLock {
        withContext(Dispatchers.IO) {
            stopCaptureResourcesLocked(discardActiveFile = true)
            CallRecordingForegroundService.stop(context)
            _recordingState.value = CallRecordingState.Idle
        }
    }

    private fun stopCaptureResourcesLocked(discardActiveFile: Boolean) {
        discardOnStop.set(discardActiveFile)
        captureRunning.set(false)
        val record = activeAudioRecord
        if (record != null) {
            runCatching { record.stop() }
        }
        val worker = captureThread
        if (worker != null && worker !== Thread.currentThread()) {
            runCatching { worker.join(2500L) }
        }
        captureThread = null
        activeAudioRecord = null
        activeRecordingCallback = null

        val writer = activeWriter
        activeWriter = null
        if (discardActiveFile) {
            runCatching { writer?.abortAndDelete() }
            runCatching { activeCaptureFile?.delete() }
        } else {
            runCatching { writer?.finishAndClose() }
        }
        activeCaptureFile = null
        _liveAudioLevel.value = 0f
        _isAudioSilenced.value = false
    }

    private fun releaseAudioRecordSafe(record: AudioRecord) {
        runCatching {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        }
        runCatching { record.release() }
    }

    suspend fun playRecording(
        fileName: String,
        storageUri: String,
        encryptedAtRest: Boolean,
        onCompletion: () -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stopPlayback()
        return@withContext try {
            if (encryptedAtRest) {
                val encryptedFile = File(legacyEncryptedDir(), fileName)
                if (!encryptedFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Recording file not found"))
                }
                val decrypted = cryptoVault.decryptBytes(encryptedFile.readBytes())
                val tempFile = File(context.cacheDir, "playback_${System.currentTimeMillis()}.m4a")
                tempFile.writeBytes(decrypted)
                activeTempPlaybackFile = tempFile
                startMediaPlayerFromPath(tempFile.absolutePath, onCompletion)
                return@withContext Result.success(Unit)
            }

            val internalFile = File(internalRecordingsDir(), fileName)
            if (internalFile.exists() && internalFile.length() > 0L) {
                startMediaPlayerFromPath(internalFile.absolutePath, onCompletion)
                return@withContext Result.success(Unit)
            }

            if (storageUri.startsWith("file://")) {
                val fileFromUri = File(Uri.parse(storageUri).path ?: "")
                if (fileFromUri.exists() && fileFromUri.length() > 0L) {
                    startMediaPlayerFromPath(fileFromUri.absolutePath, onCompletion)
                    return@withContext Result.success(Unit)
                }
            }

            if (storageUri.startsWith("content://")) {
                val uri = Uri.parse(storageUri)
                withContext(Dispatchers.Main) {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(buildPlaybackAudioAttributes())
                        setVolume(1.0f, 1.0f)
                        setDataSource(context, uri)
                        setOnCompletionListener {
                            stopPlayback()
                            onCompletion()
                        }
                        prepare()
                        start()
                    }
                    player = mp
                }
                return@withContext Result.success(Unit)
            }

            Result.failure(IllegalStateException("Recording file not found in app internal storage"))
        } catch (e: Exception) {
            stopPlayback()
            Result.failure(e)
        }
    }

    private fun buildPlaybackAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }

    private suspend fun startMediaPlayerFromPath(path: String, onCompletion: () -> Unit) {
        withContext(Dispatchers.Main) {
            val mp = MediaPlayer().apply {
                setAudioAttributes(buildPlaybackAudioAttributes())
                setVolume(1.0f, 1.0f)
                setDataSource(path)
                setOnCompletionListener {
                    stopPlayback()
                    onCompletion()
                }
                prepare()
                start()
            }
            player = mp
        }
    }

    fun stopPlayback() {
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        runCatching { activeTempPlaybackFile?.delete() }
        activeTempPlaybackFile = null
    }

    suspend fun prepareShareableRecording(
        fileName: String,
        storageUri: String,
        encryptedAtRest: Boolean
    ): Result<Pair<Uri, String>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val mimeType = when {
                fileName.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                fileName.endsWith(".m4a", ignoreCase = true) ||
                    fileName.endsWith(".mp4", ignoreCase = true) -> "audio/mp4"
                else -> "audio/*"
            }
            val authority = "${context.packageName}.fileprovider"
            val safeName = fileName.ifBlank { "call_recording_${System.currentTimeMillis()}.m4a" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")

            if (encryptedAtRest) {
                val encryptedFile = File(legacyEncryptedDir(), fileName)
                if (!encryptedFile.exists() || encryptedFile.length() <= 0L) {
                    return@withContext Result.failure(IllegalStateException("Recording file not found"))
                }
                val decrypted = cryptoVault.decryptBytes(encryptedFile.readBytes())
                val shareDir = File(context.cacheDir, "shared_recordings").apply { mkdirs() }
                val outName = if (safeName.endsWith(".enc", ignoreCase = true)) {
                    safeName.removeSuffix(".enc")
                } else {
                    safeName
                }
                val shareFile = File(shareDir, outName)
                shareFile.writeBytes(decrypted)
                val uri = FileProvider.getUriForFile(context, authority, shareFile)
                return@withContext Result.success(uri to mimeType)
            }

            val internalFile = File(internalRecordingsDir(), fileName)
            if (internalFile.exists() && internalFile.length() > 0L) {
                val uri = FileProvider.getUriForFile(context, authority, internalFile)
                return@withContext Result.success(uri to mimeType)
            }

            if (storageUri.startsWith("file://")) {
                val fileFromUri = File(Uri.parse(storageUri).path ?: "")
                if (fileFromUri.exists() && fileFromUri.length() > 0L) {
                    val uri = runCatching {
                        FileProvider.getUriForFile(context, authority, fileFromUri)
                    }.getOrElse {
                        val shareDir = File(context.cacheDir, "shared_recordings").apply { mkdirs() }
                        val copied = File(shareDir, safeName)
                        fileFromUri.copyTo(copied, overwrite = true)
                        FileProvider.getUriForFile(context, authority, copied)
                    }
                    return@withContext Result.success(uri to mimeType)
                }
            }

            if (storageUri.startsWith("content://")) {
                return@withContext Result.success(Uri.parse(storageUri) to mimeType)
            }

            Result.failure(IllegalStateException("Recording file not found"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchShareRecordingIntent(uri: Uri, mimeType: String, subject: String): Boolean {
        return runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                clipData = ClipData.newRawUri(subject, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Call Recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    fun deleteRecordingFile(fileName: String, storageUri: String = "") {
        runCatching { File(internalRecordingsDir(), fileName).delete() }
        if (storageUri.startsWith("file://")) {
            runCatching { Uri.parse(storageUri).path?.let { File(it).delete() } }
        } else if (storageUri.startsWith("content://")) {
            runCatching { context.contentResolver.delete(Uri.parse(storageUri), null, null) }
        }
        runCatching { File(legacyEncryptedDir(), fileName).delete() }
    }

    fun wipeAllRecordingFiles(recordings: List<Pair<String, String>> = emptyList()) {
        recordings.forEach { (fileName, storageUri) ->
            deleteRecordingFile(fileName, storageUri)
        }
        internalRecordingsDir().listFiles()?.forEach { it.delete() }
        legacyEncryptedDir().listFiles()?.forEach { it.delete() }
    }

    fun release() {
        stopPlayback()
        stopCaptureResourcesLocked(discardActiveFile = true)
        CallRecordingForegroundService.stop(context)
        callbackExecutor.shutdownNow()
        _recordingState.value = CallRecordingState.Idle
    }

    companion object {
        private const val INTERNAL_RECORDINGS_DIR = "call_recordings"
    }
}
