package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.preferences.RingtonePreferences
import com.example.telecom.RingtoneOption
import com.example.telecom.RingtoneSource
import com.example.ui.theme.SimAccentEmerald

@Composable
fun RingtoneSettingsSection(
    ringtones: RingtonePreferences,
    availableRingtones: List<RingtoneOption>,
    previewingUri: String?,
    onSelectRingtone: (uri: String, title: String) -> Unit,
    onTogglePreview: (uri: String, vibrate: Boolean) -> Unit,
    onStopPreview: () -> Unit,
    onOpenSystemPicker: () -> Unit,
    onOpenCustomAudioPicker: () -> Unit,
    onSetVibrateSim: (Boolean) -> Unit,
    onResetDefaults: () -> Unit,
    modifier: Modifier = Modifier
) {
    DisposableEffect(Unit) {
        onDispose { onStopPreview() }
    }

    val activeAccent = SimAccentEmerald
    val activeUri = ringtones.simRingtoneUri
    val activeTitle = ringtones.simRingtoneTitle
    val activeVibrate = ringtones.vibrateOnSimRing
    val isActivePlaying = previewingUri == activeUri

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active SIM Cellular Ringtone Summary Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, activeAccent.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ringtone_summary_sim")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "SIM Ringtone settings",
                            tint = activeAccent
                        )
                        Column {
                            Text(
                                text = "SIM Cellular Ringtone",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = activeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = activeAccent,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onTogglePreview(activeUri, activeVibrate) },
                            modifier = Modifier.testTag("ringtone_preview_active_button")
                        ) {
                            Icon(
                                imageVector = if (isActivePlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isActivePlaying) "Stop active ringtone" else "Play active ringtone",
                                tint = activeAccent
                            )
                        }
                        TextButton(
                            onClick = onResetDefaults,
                            modifier = Modifier.testTag("reset_ringtones_button")
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }

                // SIM Vibration Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Vibration,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Vibrate on Incoming SIM Calls",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (activeVibrate) "Sound + Vibrate enabled" else "Sound only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = activeVibrate,
                        onCheckedChange = onSetVibrateSim,
                        modifier = Modifier.testTag("ringtone_vibrate_switch")
                    )
                }

                // Zero-Permission System & Custom Audio Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = onOpenSystemPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ringtone_system_picker_button")
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("System Picker", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onOpenCustomAudioPicker,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ringtone_custom_audio_button")
                    ) {
                        Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Custom Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        val displayedRingtones = if (
            activeUri.isNotBlank() && availableRingtones.none { it.uri == activeUri }
        ) {
            val source = if (activeUri.startsWith("file://")) {
                RingtoneSource.CUSTOM
            } else {
                RingtoneSource.SYSTEM
            }
            val subtitle = if (source == RingtoneSource.CUSTOM) {
                "Selected custom audio file"
            } else {
                "Selected from system ringtone picker"
            }
            listOf(
                RingtoneOption(
                    uri = activeUri,
                    title = activeTitle,
                    subtitle = subtitle,
                    source = source
                )
            ) + availableRingtones
        } else {
            availableRingtones
        }

        // Built-In & Device Ringtone Catalog
        Text(
            text = "Choose Incoming Call Ringtone (${displayedRingtones.size})",
            style = MaterialTheme.typography.titleSmall,
            color = activeAccent
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            displayedRingtones.forEach { option ->
                val isSelected = activeUri == option.uri
                val isPreviewing = previewingUri == option.uri
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        activeAccent.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) activeAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onSelectRingtone(option.uri, option.title)
                        }
                        .testTag("ringtone_option_${option.title.replace(" ", "_").lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectRingtone(option.uri, option.title) }
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (option.source == RingtoneSource.BUILTIN) {
                                        activeAccent.copy(alpha = 0.16f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHighest
                                    }
                                ) {
                                    Text(
                                        text = option.source.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (option.source == RingtoneSource.BUILTIN) {
                                            activeAccent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = option.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onTogglePreview(option.uri, activeVibrate) },
                            modifier = Modifier.testTag("preview_ringtone_${option.uri.hashCode()}")
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPreviewing) "Stop preview" else "Preview ${option.title}",
                                tint = if (isPreviewing) activeAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
