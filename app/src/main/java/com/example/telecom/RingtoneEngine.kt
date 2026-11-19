package com.example.telecom

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * Manages SIM ringtone discovery, zero-permission system/custom URI resolution,
 * synthesized melodic built-in ringtones via AudioTrack, and MediaPlayer/RingtoneManager
 * playback with optional vibration feedback.
 */
class RingtoneEngine(private val context: Context) {

    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var synthJob: Job? = null
    private var activeRingtone: Ringtone? = null
    private var activeMediaPlayer: MediaPlayer? = null

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
                val defaultTitle = RingtoneManager.getRingtone(context, defaultUri)?.getTitle(context)
                    ?.takeIf { it.isNotBlank() } ?: "Device Default"
                systemOptions.add(
                    RingtoneOption(
                        uri = defaultUri.toString(),
                        title = "System Default ($defaultTitle)",
                        subtitle = "Android system ringtone",
                        source = RingtoneSource.SYSTEM
                    )
                )
            }

            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = manager.cursor
            var count = 0
            if (cursor != null) {
                while (cursor.moveToNext() && count < 18) {
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
        } catch (_: Exception) {
            // Device may restrict ringtone cursor on headless emulators; built-in tones remain available.
        }
        return builtInRingtones + systemOptions
    }

    fun createSystemRingtonePickerIntent(currentUri: String, title: String): Intent {
        val existingUri = when {
            currentUri.startsWith("content://") -> runCatching { Uri.parse(currentUri) }.getOrNull()
            else -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }
        return Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, title)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            if (existingUri != null) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            }
        }
    }

    fun resolveTitleForUri(uri: Uri?): String {
        if (uri == null) return "Silent (No Sound)"
        try {
            val ringtoneTitle = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
            if (!ringtoneTitle.isNullOrBlank() && !ringtoneTitle.equals("Unknown ringtone", ignoreCase = true)) {
                return ringtoneTitle
            }
        } catch (_: Exception) {
        }
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
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

    fun playPreviewOrRing(
        uriString: String,
        looping: Boolean,
        vibrate: Boolean,
        onFinished: () -> Unit = {}
    ) {
        stop()
        if (vibrate) {
            triggerVibration(looping)
        }
        if (uriString == "silent" || uriString.isBlank()) {
            if (!looping) onFinished()
            return
        }

        if (uriString.startsWith("builtin:")) {
            playBuiltInTone(uriString, looping, onFinished)
            return
        }

        try {
            val parsedUri = Uri.parse(uriString)
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, parsedUri)
                isLooping = looping
                setOnCompletionListener {
                    if (!looping) {
                        stop()
                        onFinished()
                    }
                }
                setOnErrorListener { _, _, _ ->
                    stop()
                    onFinished()
                    true
                }
                prepare()
                start()
            }
            activeMediaPlayer = mp
        } catch (_: Exception) {
            // Fallback to Android RingtoneManager playback
            try {
                val rt = RingtoneManager.getRingtone(context, Uri.parse(uriString))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    rt?.isLooping = looping
                }
                rt?.play()
                activeRingtone = rt
            } catch (_: Exception) {
                playBuiltInTone("builtin:sim_classic", looping, onFinished)
            }
        }
    }

    private fun playBuiltInTone(
        toneId: String,
        looping: Boolean,
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

        synthJob = playbackScope.launch {
            var track: AudioTrack? = null
            try {
                val pcm = synthesizeMelodyPcm(notes, sampleRate)
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcm.size * 2)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
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

                track.play()
                val cycles = if (looping) 100 else 1
                var i = 0
                while (isActive && i < cycles) {
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
                if (!looping) {
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

    private fun triggerVibration(looping: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (looping) longArrayOf(0, 350, 500) else longArrayOf(0, 180, 120, 180)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, if (looping) 0 else -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(200L)
            }
        } catch (_: Exception) {
        }
    }

    fun stop() {
        synthJob?.cancel()
        synthJob = null
        try {
            activeMediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            activeMediaPlayer?.release()
        } catch (_: Exception) {
        }
        activeMediaPlayer = null
        try {
            activeRingtone?.stop()
        } catch (_: Exception) {
        }
        activeRingtone = null
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.cancel()
        } catch (_: Exception) {
        }
    }

    fun release() {
        stop()
        playbackScope.cancel()
    }
}
