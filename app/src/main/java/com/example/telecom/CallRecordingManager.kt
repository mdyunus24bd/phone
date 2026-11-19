package com.example.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.security.CryptoVault
import kotlinx.coroutines.Dispatchers
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

data class FinishedRecording(
    val fileName: String,
    val storageUri: String,
    val storagePath: String,
    val durationSec: Long,
    val fileSizeBytes: Long,
    val encryptedAtRest: Boolean
)

sealed class CallRecordingState {
    data object Idle : CallRecordingState()
    data object Starting : CallRecordingState()
    data class Recording(
        val startedAtMs: Long,
        val callerLabel: String,
        val networkType: String,
        val isAutoTriggered: Boolean,
        val audioSourceLabel: String
    ) : CallRecordingState()
    data object Saving : CallRecordingState()
    data class Error(val message: String) : CallRecordingState()
}

/**
 * Dedicated CallRecordingManager built on Android's MediaRecorder API to handle
 * call recording safely on Dispatchers.IO, backed by a microphone-typed Foreground
 * Service and saving finalized .m4a audio files directly into the app's private
 * internal storage (`context.filesDir/call_recordings`).
 */
class CallRecordingManager(
    private val context: Context,
    private val cryptoVault: CryptoVault
) {
    private val recorderMutex = Mutex()
    private var mediaRecorder: MediaRecorder? = null
    private var activeRecordingFile: File? = null
    private var startedAtMs: Long = 0L

    private var player: MediaPlayer? = null
    private var activeTempPlaybackFile: File? = null

    private val _recordingState = MutableStateFlow<CallRecordingState>(CallRecordingState.Idle)
    val recordingState: StateFlow<CallRecordingState> = _recordingState.asStateFlow()

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

    /**
     * Human-readable App Internal Storage folder displayed in settings and recording cards.
     */
    fun internalStorageFolderLabel(): String = "Internal Storage / $INTERNAL_RECORDINGS_DIR"

    /**
     * Starts call recording using Android's MediaRecorder API, writing audio directly into
     * the app's private internal storage directory and keeping capture alive with
     * CallRecordingForegroundService (microphone foreground service).
     * Automatically falls back from VOICE_COMMUNICATION to MIC if the device rejects
     * the voice communication audio source.
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

        withContext(Dispatchers.IO) {
            stopRecorderResourcesLocked(discardActiveFile = true)

            // Start microphone foreground service so recording continues reliably during calls
            CallRecordingForegroundService.start(
                context = context,
                callerLabel = callerLabel,
                networkType = networkType
            )

            val recordingsDir = internalRecordingsDir()
            val candidateSources = listOf(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMM",
                MediaRecorder.AudioSource.MIC to "MIC"
            )

            var lastError: Exception? = null
            for ((audioSource, sourceLabel) in candidateSources) {
                val captureFile = File(recordingsDir, "active_capture_${System.currentTimeMillis()}.m4a")
                val candidate = createMediaRecorder()
                try {
                    candidate.apply {
                        setAudioSource(audioSource)
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioSamplingRate(44100)
                        setAudioEncodingBitRate(96000)
                        setOutputFile(captureFile.absolutePath)
                        setOnErrorListener { _, what, extra ->
                            _recordingState.value = CallRecordingState.Error(
                                "MediaRecorder error ($what/$extra)"
                            )
                            CallRecordingForegroundService.stop(context)
                        }
                        prepare()
                        start()
                    }
                    mediaRecorder = candidate
                    activeRecordingFile = captureFile
                    startedAtMs = System.currentTimeMillis()
                    _recordingState.value = CallRecordingState.Recording(
                        startedAtMs = startedAtMs,
                        callerLabel = callerLabel,
                        networkType = networkType,
                        isAutoTriggered = isAutoTriggered,
                        audioSourceLabel = sourceLabel
                    )
                    return@withContext Result.success(sourceLabel)
                } catch (e: Exception) {
                    lastError = e
                    runCatching { candidate.reset() }
                    runCatching { candidate.release() }
                    runCatching { captureFile.delete() }
                }
            }

            CallRecordingForegroundService.stop(context)
            val failure = lastError ?: IllegalStateException("Unable to initialize MediaRecorder")
            _recordingState.value = CallRecordingState.Error(
                failure.localizedMessage ?: "Audio device unavailable"
            )
            Result.failure(failure)
        }
    }

    /**
     * Stops the active MediaRecorder session on a background thread, stops the foreground
     * service, and finalizes the .m4a audio recording in the app's internal storage.
     */
    suspend fun stopAndSaveRecording(
        phoneNumber: String,
        networkType: String
    ): Result<FinishedRecording> = recorderMutex.withLock {
        withContext(Dispatchers.IO) {
            val rawFile = activeRecordingFile
            if (rawFile == null) {
                CallRecordingForegroundService.stop(context)
                _recordingState.value = CallRecordingState.Idle
                return@withContext Result.failure(IllegalStateException("No active call recording"))
            }

            _recordingState.value = CallRecordingState.Saving
            val durationSec = ((System.currentTimeMillis() - startedAtMs) / 1000L).coerceAtLeast(1L)
            stopRecorderResourcesLocked(discardActiveFile = false)
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
                val fileName = "VoxLine_${networkType}_${safePeer}_${timestamp}.m4a"
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
                        encryptedAtRest = false
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
            stopRecorderResourcesLocked(discardActiveFile = true)
            CallRecordingForegroundService.stop(context)
            _recordingState.value = CallRecordingState.Idle
        }
    }

    private fun createMediaRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    private fun stopRecorderResourcesLocked(discardActiveFile: Boolean) {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.reset()
        } catch (_: Exception) {
        }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
        if (discardActiveFile) {
            runCatching { activeRecordingFile?.delete() }
        }
        activeRecordingFile = null
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

            // Primary lookup: App Internal Storage (context.filesDir/call_recordings)
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

            // Backward compatibility for any legacy content:// URI recordings
            if (storageUri.startsWith("content://")) {
                val uri = Uri.parse(storageUri)
                withContext(Dispatchers.Main) {
                    val mp = MediaPlayer().apply {
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

    private suspend fun startMediaPlayerFromPath(path: String, onCompletion: () -> Unit) {
        withContext(Dispatchers.Main) {
            val mp = MediaPlayer().apply {
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
        stopRecorderResourcesLocked(discardActiveFile = true)
        CallRecordingForegroundService.stop(context)
        _recordingState.value = CallRecordingState.Idle
    }

    companion object {
        private const val INTERNAL_RECORDINGS_DIR = "call_recordings"
    }
}
