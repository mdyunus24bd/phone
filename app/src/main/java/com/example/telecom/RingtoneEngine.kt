package com.example.telecom

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.provider.Settings
import com.example.data.preferences.RingtonePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class RingtoneSource(val label: String) {
    BUILTIN("Built-in"),
    SYSTEM("System"),
    CUSTOM("Custom Audio")
}

data class RingtoneOption(
    val uri: String,
    val title: String,
    val subtitle: String,
    val source: RingtoneSource
)

/**
 * Manages SIM ringtone discovery, local caching of custom/external audio URIs so they never lose
 * read permission in background InCallService, synthesized melodic built-in ringtones via
 * AudioTrack, and MediaPlayer / RingtoneManager playback with audio focus and vibration support.
 */
class RingtoneEngine(context: Context) {

    private val appContext: Context = context.applicationContext
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val playbackGeneration = AtomicLong(0L)
    private val playerLock = Any()

    private var playbackJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var activeMediaPlayer: MediaPlayer? = null
    private var activeFocusRequest: AudioFocusRequest? = null

    val builtInRingtones: List<RingtoneOption> = listOf(
        RingtoneOption(
            uri = "builtin:sim_classic",
            title = "Classic Horizon",
            subtitle = "Warm dual-harmonic SIM cellular ring",
            source = RingtoneSource.BUILTIN
        ),
        RingtoneOption(
            uri = "builtin:sim_emerald",
            title = "Emerald Bell",
            subtitle = "Bright ascending cellular chime",
            source = RingtoneSource.BUILTIN
        ),
        RingtoneOption(
            uri = "builtin:sim_crystal",
            title = "Crystal Pulse",
            subtitle = "Crisp harmonic digital arpeggio",
            source = RingtoneSource.BUILTIN
        ),
        RingtoneOption(
            uri = "builtin:sim_aurora",
            title = "Aurora Chime",
            subtitle = "Ambient melodic chord ringtone",
            source = RingtoneSource.BUILTIN
        ),
        RingtoneOption(
            uri = "builtin:sim_priority",
            title = "Priority Beacon",
            subtitle = "Clear double-pulse office line tone",
            source = RingtoneSource.BUILTIN
        ),
        RingtoneOption(
            uri = "silent",
            title = "Silent (No Sound)",
            subtitle = "Mute ringtone audio for incoming calls",
            source = RingtoneSource.BUILTIN
        )
    )

    /**
     * Reads installed Android device ringtones via RingtoneManager (zero extra permissions).
     */
    fun loadAvailableRingtones(): List<RingtoneOption> {
        val systemOptions = mutableListOf<RingtoneOption>()
        try {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (defaultUri != null) {
                val actualDefault = runCatching {
                    RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
                }.getOrNull() ?: defaultUri
                val defaultTitle = runCatching {
                    RingtoneManager.getRingtone(appContext, actualDefault)?.getTitle(appContext)
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Device Default"
                systemOptions.add(
                    RingtoneOption(
                        uri = defaultUri.toString(),
                        title = "System Default ($defaultTitle)",
                        subtitle = "Follows Android phone default ringtone",
                        source = RingtoneSource.SYSTEM
                    )
                )
            }

            val manager = RingtoneManager(appContext).apply {
                setType(RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = manager.cursor
            try {
                var count = 0
                if (cursor != null) {
                    while (cursor.moveToNext() && count < 50) {
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                        val uri = manager.getRingtoneUri(cursor.position)?.toString() ?: continue
                        if (systemOptions.none { it.uri == uri }) {
                            systemOptions.add(
                                RingtoneOption(
                                    uri = uri,
                                    title = title,
                                    subtitle = "Device ringtone",
                                    source = RingtoneSource.SYSTEM
                                )
                            )
                            count++
                        }
                    }
                }
            } finally {
                runCatching { cursor?.close() }
            }
        } catch (_: Exception) {
            // Device may restrict ringtone cursor on headless emulators; built-in tones remain available.
        }
        return builtInRingtones + systemOptions
    }

    fun createSystemRingtonePickerIntent(currentUri: String, title: String): Intent {
        val existingUri: Uri? = when {
            currentUri == "silent" -> null
            currentUri.startsWith("content://") -> runCatching { Uri.parse(currentUri) }.getOrNull()
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
        }
    }

    fun resolveTitleForUri(uri: Uri?): String {
        if (uri == null) return "Silent (No Sound)"
        if (uri.scheme == "file") {
            val fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
            if (!fileName.isNullOrBlank()) return fileName
        }
        try {
            val ringtoneTitle = RingtoneManager.getRingtone(appContext, uri)?.getTitle(appContext)
            if (!ringtoneTitle.isNullOrBlank() &&
                !ringtoneTitle.equals("Unknown ringtone", ignoreCase = true)
            ) {
                return ringtoneTitle
            }
        } catch (_: Exception) {
        }
        try {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) {
                            return name.substringBeforeLast('.')
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.ifBlank { "Custom Audio" }
            ?: "Custom Audio"
    }

    /**
     * Copies a picked custom or external audio file into app-private storage so VoxLineInCallService
     * can always play it reliably during incoming calls without losing temporary URI read grants.
     * System internal ringtones (`content://settings/...`, `content://media/internal/...`) are kept
     * as their direct URIs unless [forceCopy] is true.
     */
    fun persistRingtoneUriIfNeeded(sourceUri: Uri, forceCopy: Boolean): String {
        val raw = sourceUri.toString()
        val isDirectSystemUri = !forceCopy && (
            sourceUri.authority == "settings" ||
                raw.startsWith("content://media/internal/", ignoreCase = true)
            )
        if (isDirectSystemUri) {
            return raw
        }

        return try {
            val resolver = appContext.contentResolver
            val ext = resolveExtension(sourceUri)
            val dir = File(appContext.filesDir, "custom_ringtones").apply { mkdirs() }
            // Clean up older cached custom ringtones so internal storage stays tidy
            dir.listFiles()?.forEach { existing ->
                runCatching { existing.delete() }
            }
            val destFile = File(dir, "sim_ringtone_${System.currentTimeMillis()}.$ext")
            val copiedBytes = resolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: 0L

            if (copiedBytes > 0L && destFile.exists() && destFile.length() > 0L) {
                Uri.fromFile(destFile).toString()
            } else {
                runCatching { destFile.delete() }
                raw
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun resolveExtension(uri: Uri): String {
        val mime = runCatching { appContext.contentResolver.getType(uri) }.getOrNull().orEmpty()
        return when {
            mime.contains("mpeg", ignoreCase = true) || mime.contains("mp3", ignoreCase = true) -> "mp3"
            mime.contains("ogg", ignoreCase = true) -> "ogg"
            mime.contains("wav", ignoreCase = true) -> "wav"
            mime.contains("flac", ignoreCase = true) -> "flac"
            mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true) -> "m4a"
            mime.contains("aac", ignoreCase = true) -> "aac"
            else -> {
                val seg = uri.lastPathSegment?.substringAfterLast('.', "").orEmpty()
                if (seg.length in 2..5 && seg.all { it.isLetterOrDigit() }) seg.lowercase() else "audio"
            }
        }
    }

    fun playPreviewOrRing(
        uriString: String,
        looping: Boolean,
        vibrate: Boolean,
        respectRingerMode: Boolean = false,
        onFinished: () -> Unit = {}
    ) {
        val gen = stopInternal()

        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val ringerMode = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

        val allowSound = !respectRingerMode || ringerMode == AudioManager.RINGER_MODE_NORMAL
        val allowVibrate = vibrate && (!respectRingerMode || ringerMode != AudioManager.RINGER_MODE_SILENT)

        if (allowVibrate) {
            triggerVibration(looping)
        }

        if (!allowSound || uriString == "silent" || uriString.isBlank()) {
            if (!looping) onFinished()
            return
        }

        if (looping) {
            requestRingtoneAudioFocus(audioManager)
        }

        if (uriString.startsWith("builtin:")) {
            playBuiltInTone(uriString, looping, gen, onFinished)
            return
        }

        playbackJob = playbackScope.launch {
            if (playbackGeneration.get() != gen) return@launch

            val parsedUri = runCatching { Uri.parse(uriString) }.getOrNull()
            val resolvedUri = resolvePlayableUri(parsedUri)
            if (resolvedUri == null) {
                // Device default ringtone is explicitly configured as Silent
                if (!looping) onFinished()
                return@launch
            }

            val startedWithMediaPlayer = tryPlayWithMediaPlayer(
                uri = resolvedUri,
                looping = looping,
                gen = gen,
                onFinished = onFinished
            )
            if (startedWithMediaPlayer || playbackGeneration.get() != gen) {
                return@launch
            }

            val startedWithRingtone = tryPlayWithSystemRingtone(
                uri = resolvedUri,
                looping = looping,
                gen = gen,
                onFinished = onFinished
            )
            if (startedWithRingtone || playbackGeneration.get() != gen) {
                return@launch
            }

            // Final safety fallback if URI became unreadable
            playBuiltInTone("builtin:sim_classic", looping, gen, onFinished)
        }
    }

    private fun resolvePlayableUri(uri: Uri?): Uri? {
        if (uri == null) return null
        val isSystemDefaultAlias = uri == Settings.System.DEFAULT_RINGTONE_URI ||
            uri.toString().equals(Settings.System.DEFAULT_RINGTONE_URI.toString(), ignoreCase = true)
        if (isSystemDefaultAlias) {
            return runCatching {
                RingtoneManager.getActualDefaultRingtoneUri(appContext, RingtoneManager.TYPE_RINGTONE)
            }.getOrElse { uri }
        }
        return uri
    }

    private fun buildRingtoneAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private fun tryPlayWithMediaPlayer(
        uri: Uri,
        looping: Boolean,
        gen: Long,
        onFinished: () -> Unit
    ): Boolean {
        var mp: MediaPlayer? = null
        return try {
            mp = MediaPlayer().apply {
                setAudioAttributes(buildRingtoneAudioAttributes())
                if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                    val file = File(uri.path!!)
                    if (!file.exists() || !file.canRead()) {
                        throw IllegalStateException("Cached ringtone file missing")
                    }
                    FileInputStream(file).use { fis ->
                        setDataSource(fis.fd)
                    }
                } else {
                    setDataSource(appContext, uri)
                }
                isLooping = looping
                setOnCompletionListener {
                    if (!looping && playbackGeneration.get() == gen) {
                        stop()
                        onFinished()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    if (playbackGeneration.get() == gen) {
                        stop()
                        onFinished()
                    }
                    true
                }
                prepare()
            }

            synchronized(playerLock) {
                if (playbackGeneration.get() != gen) {
                    runCatching { mp.release() }
                    return true
                }
                activeMediaPlayer = mp
            }
            mp.start()
            true
        } catch (_: Exception) {
            runCatching { mp?.release() }
            false
        }
    }

    private suspend fun tryPlayWithSystemRingtone(
        uri: Uri,
        looping: Boolean,
        gen: Long,
        onFinished: () -> Unit
    ): Boolean {
        return try {
            val rt = RingtoneManager.getRingtone(appContext, uri) ?: return false
            rt.audioAttributes = buildRingtoneAudioAttributes()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rt.isLooping = looping
            }
            synchronized(playerLock) {
                if (playbackGeneration.get() != gen) {
                    return true
                }
                activeRingtone = rt
            }
            rt.play()

            // Monitor completion (for previews) or manual re-looping on API < 28
            if (!looping || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                val maxChecks = if (looping) 600 else 150
                var checks = 0
                delay(300L)
                while (playbackGeneration.get() == gen && checks < maxChecks) {
                    val stillPlaying = runCatching { rt.isPlaying }.getOrDefault(false)
                    if (!stillPlaying) {
                        if (looping) {
                            runCatching { rt.play() }
                        } else {
                            if (playbackGeneration.get() == gen) {
                                stop()
                                onFinished()
                            }
                            break
                        }
                    }
                    delay(350L)
                    checks++
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun playBuiltInTone(
        toneId: String,
        looping: Boolean,
        gen: Long,
        onFinished: () -> Unit
    ) {
        val sampleRate = 22050
        val notes: List<Pair<Double, Int>> = when (toneId) {
            "builtin:sim_emerald" -> listOf(
                523.25 to 170, 659.25 to 170, 783.99 to 190, 1046.50 to 320, 0.0 to 280
            )
            "builtin:sim_crystal" -> listOf(
                880.0 to 130, 1108.73 to 130, 1318.51 to 160, 0.0 to 90,
                1108.73 to 130, 1318.51 to 240, 0.0 to 260
            )
            "builtin:sim_aurora" -> listOf(
                440.0 to 220, 554.37 to 220, 659.25 to 260, 880.0 to 360, 0.0 to 300
            )
            "builtin:sim_priority" -> listOf(
                987.77 to 140, 0.0 to 70, 987.77 to 140, 0.0 to 140, 783.99 to 220, 0.0 to 320
            )
            else -> listOf(
                659.25 to 180, 523.25 to 180, 659.25 to 260, 0.0 to 120,
                587.33 to 180, 783.99 to 280, 0.0 to 260
            )
        }

        playbackJob = playbackScope.launch {
            var track: AudioTrack? = null
            try {
                val pcm = synthesizeMelodyPcm(notes, sampleRate)
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcm.size * 2)

                track = AudioTrack.Builder()
                    .setAudioAttributes(buildRingtoneAudioAttributes())
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (playbackGeneration.get() != gen) return@launch
                track.play()
                val cycles = if (looping) 120 else 1
                var i = 0
                while (isActive && playbackGeneration.get() == gen && i < cycles) {
                    track.write(pcm, 0, pcm.size)
                    i++
                }
            } catch (_: Exception) {
                // Headless or audio-restricted device
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Exception) {
                }
                if (!looping && playbackGeneration.get() == gen) {
                    onFinished()
                }
            }
        }
    }

    private fun synthesizeMelodyPcm(notes: List<Pair<Double, Int>>, sampleRate: Int): ShortArray {
        val totalSamples = notes.sumOf { (sampleRate * it.second) / 1000 }
        val output = ShortArray(totalSamples)
        var offset = 0
        for ((freq, durationMs) in notes) {
            val count = (sampleRate * durationMs) / 1000
            if (freq <= 0.0) {
                offset += count
                continue
            }
            for (n in 0 until count) {
                val t = n.toDouble() / sampleRate
                val attack = (n.toDouble() / (sampleRate * 0.012)).coerceAtMost(1.0)
                val decay = exp(-3.5 * t)
                val fundamental = sin(2.0 * PI * freq * t)
                val overtone = 0.35 * sin(2.0 * PI * freq * 2.0 * t)
                val sample = ((fundamental + overtone) * attack * decay * 14000.0)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                if (offset + n < output.size) {
                    output[offset + n] = sample.toShort()
                }
            }
            offset += count
        }
        return output
    }

    private fun requestRingtoneAudioFocus(audioManager: AudioManager?) {
        if (audioManager == null) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(buildRingtoneAudioAttributes())
                    .setWillPauseWhenDucked(false)
                    .build()
                activeFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_RING,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun abandonRingtoneAudioFocus() {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let { req ->
                    audioManager.abandonAudioFocusRequest(req)
                }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (_: Exception) {
        }
    }

    private fun triggerVibration(looping: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (looping) longArrayOf(0, 400, 600) else longArrayOf(0, 180, 120, 180)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, if (looping) 0 else -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (looping) longArrayOf(0, 400, 600) else longArrayOf(0, 200), if (looping) 0 else -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun stopInternal(): Long {
        val nextGen = playbackGeneration.incrementAndGet()
        playbackJob?.cancel()
        playbackJob = null

        val mpToRelease: MediaPlayer?
        val rtToStop: Ringtone?
        synchronized(playerLock) {
            mpToRelease = activeMediaPlayer
            activeMediaPlayer = null
            rtToStop = activeRingtone
            activeRingtone = null
        }

        try {
            mpToRelease?.stop()
        } catch (_: Exception) {
        }
        try {
            mpToRelease?.release()
        } catch (_: Exception) {
        }
        try {
            rtToStop?.stop()
        } catch (_: Exception) {
        }
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        abandonRingtoneAudioFocus()
        return nextGen
    }

    fun stop() {
        stopInternal()
    }

    fun release() {
        stopInternal()
        playbackScope.cancel()
    }
}

/**
 * Process-wide singleton ringer owned by VoxLineInCallService so incoming calls ALWAYS play
 * the user's persisted ringtone preference — even on cold start, lock screen, or background
 * heads-up calls before MainActivity is open — without duplicate overlapping audio.
 */
object IncomingCallRinger {
    private val lock = Any()
    private var engine: RingtoneEngine? = null
    private var activeCallKey: String? = null
    private var activeRingtoneSignature: String? = null

    private fun getOrCreateEngine(context: Context): RingtoneEngine {
        return synchronized(lock) {
            engine ?: RingtoneEngine(context.applicationContext).also { engine = it }
        }
    }

    fun startIncomingRing(
        context: Context,
        callKey: String,
        preferences: RingtonePreferences
    ) {
        val signature = "${preferences.simRingtoneUri}|${preferences.vibrateOnSimRing}"
        val shouldStart = synchronized(lock) {
            if (activeCallKey == callKey && activeRingtoneSignature == signature) {
                false
            } else {
                activeCallKey = callKey
                activeRingtoneSignature = signature
                true
            }
        }
        if (!shouldStart) return

        getOrCreateEngine(context).playPreviewOrRing(
            uriString = preferences.simRingtoneUri,
            looping = true,
            vibrate = preferences.vibrateOnSimRing,
            respectRingerMode = true
        )
    }

    fun stop() {
        val currentEngine = synchronized(lock) {
            activeCallKey = null
            activeRingtoneSignature = null
            engine
        }
        currentEngine?.stop()
    }
}
