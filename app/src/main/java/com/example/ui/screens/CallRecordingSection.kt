package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.data.preferences.AutoRecordMode
import com.example.data.preferences.VoxLineSettings
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallRecordingSection(
    settings: VoxLineSettings,
    storageFolderLabel: String,
    contacts: List<ContactEntity>,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    onToggleAutoRecord: (Boolean) -> Unit,
    onSelectAutoRecordMode: (AutoRecordMode) -> Unit,
    onAddAutoRecordNumber: (String) -> Unit,
    onRemoveAutoRecordNumber: (String) -> Unit,
    onClearAutoRecordNumbers: () -> Unit,
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onShareRecording: (CallRecordingEntity) -> Unit = {},
    onDeleteRecording: (CallRecordingEntity) -> Unit
) {
    var numberInput by remember { mutableStateOf("") }

    val sortedNumbers = remember(settings.autoRecordNumbers) {
        settings.autoRecordNumbers.sorted()
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Master Automatic Call Recording Toggle + Mode Selector
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
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
                            imageVector = Icons.Filled.FiberManualRecord,
                            contentDescription = "Automatic Call Recording",
                            tint = if (settings.autoRecordCalls) CallEndRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Automatic Call Recording",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (settings.autoRecordCalls) {
                                    "Enabled • ${settings.autoRecordMode.description}"
                                } else {
                                    "Disabled • Record calls manually from active call screen"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = settings.autoRecordCalls,
                        onCheckedChange = onToggleAutoRecord,
                        modifier = Modifier.testTag("toggle_auto_call_recording")
                    )
                }

                // Mode Switcher: All Calls vs Specific Numbers
                Text(
                    text = "Recording Trigger Rule",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auto_record_mode_selector")
                ) {
                    AutoRecordMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.autoRecordMode == mode,
                            onClick = { onSelectAutoRecordMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AutoRecordMode.entries.size
                            ),
                            icon = {
                                Icon(
                                    imageVector = if (mode == AutoRecordMode.ALL_CALLS) {
                                        Icons.Filled.PhoneInTalk
                                    } else {
                                        Icons.Filled.PersonAddAlt1
                                    },
                                    contentDescription = mode.label,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("auto_record_mode_${mode.name}")
                        ) {
                            Text(mode.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // Specific Number Auto-Record Manager
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = if (settings.autoRecordCalls && settings.autoRecordMode == AutoRecordMode.SPECIFIC_NUMBERS) {
                BorderStroke(1.dp, SimAccentEmerald.copy(alpha = 0.55f))
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Specific Number Recording List (${sortedNumbers.size})",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Add phone numbers to automatically record when Specific Numbers mode is selected.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (sortedNumbers.isNotEmpty()) {
                        TextButton(
                            onClick = onClearAutoRecordNumbers,
                            modifier = Modifier.testTag("clear_auto_record_numbers")
                        ) {
                            Text("Clear")
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = numberInput,
                        onValueChange = { numberInput = it },
                        label = { Text("Phone number") },
                        placeholder = { Text("+15551234567") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auto_record_number_input")
                    )
                    Button(
                        onClick = {
                            onAddAutoRecordNumber(numberInput)
                            numberInput = ""
                        },
                        enabled = numberInput.isNotBlank(),
                        modifier = Modifier.testTag("add_auto_record_number_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                // Quick-Pick from Saved Contacts
                val pickableContacts = remember(contacts) {
                    contacts.filter { it.phoneNumber.isNotBlank() }.take(10)
                }
                AnimatedVisibility(visible = pickableContacts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quick Select from Contacts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(pickableContacts, key = { it.id }) { contact ->
                                val address = contact.phoneNumber
                                val isAdded = settings.autoRecordNumbers.contains(address)
                                FilterChip(
                                    selected = isAdded,
                                    onClick = {
                                        if (isAdded) onRemoveAutoRecordNumber(address)
                                        else onAddAutoRecordNumber(address)
                                    },
                                    label = {
                                        Text(
                                            text = "${contact.displayName} • $address",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // Active Specific Numbers List
                if (sortedNumbers.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No specific numbers added yet. Enter a number above or tap any contact chip to start recording calls with that number automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        sortedNumbers.forEach { number ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = number,
                                            style = MonoMetricStyle,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Auto-record active • Saves to App Internal Storage",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SimAccentEmerald
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveAutoRecordNumber(number) },
                                        modifier = Modifier.testTag("remove_auto_record_$number")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Remove $number from auto-record list"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Saved App Internal Storage Recordings List
        if (recordings.isNotEmpty()) {
            Text(
                text = "Saved Recordings in App Internal Storage (${recordings.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            recordings.forEach { rec ->
                val isPlaying = playingRecordingId == rec.id
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconButton(onClick = { onTogglePlayback(rec) }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Stop playback" else "Play recording",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = rec.displayName.ifBlank { rec.phoneNumber },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (rec.autoRecorded) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = CallEndRed.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = "AUTO",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CallEndRed,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = rec.storagePath.ifBlank { rec.fileName },
                                style = MonoMetricStyle,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${formatDuration(rec.durationSec)} • ${rec.fileSizeBytes / 1024} KB • ${formatTimestamp(rec.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onShareRecording(rec) },
                            modifier = Modifier.testTag("share_recording_${rec.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share recording",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { onDeleteRecording(rec) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete recording from app internal storage",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
