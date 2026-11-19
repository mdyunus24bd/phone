package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.ui.theme.CallEndRed

private val QUICK_CALL_NOTE_PRESETS = listOf(
    "Call back later",
    "Left voicemail",
    "Follow up",
    "Important",
    "Sent message"
)

private const val MAX_CALL_NOTE_LENGTH = 240

/**
 * Dialog for adding, editing, or clearing a quick text note on an individual call log entry.
 */
@Composable
fun QuickCallNoteDialog(
    log: CallLogEntity,
    callerLabel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteText by remember(log.id, log.quickNote) { mutableStateOf(log.quickNote) }
    val hasExistingNote = log.quickNote.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.EditNote,
                contentDescription = "Quick call note",
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hasExistingNote) "Edit Call Note" else "Add Quick Call Note",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$callerLabel • ${formatTimestamp(log.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { updated ->
                        if (updated.length <= MAX_CALL_NOTE_LENGTH) {
                            noteText = updated
                        }
                    },
                    label = { Text("Quick note") },
                    placeholder = { Text("e.g. Discussed invoice #402, follow up Friday") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text("${noteText.length}/$MAX_CALL_NOTE_LENGTH")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("call_note_input")
                )

                Text(
                    text = "Quick tags",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(QUICK_CALL_NOTE_PRESETS) { preset ->
                        SuggestionChip(
                            onClick = {
                                val trimmed = noteText.trim()
                                val combined = when {
                                    trimmed.isEmpty() -> preset
                                    trimmed.contains(preset, ignoreCase = true) -> trimmed
                                    else -> "$trimmed • $preset"
                                }
                                noteText = combined.take(MAX_CALL_NOTE_LENGTH)
                            },
                            label = {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.testTag("call_note_preset_${preset.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                if (hasExistingNote) {
                    TextButton(
                        onClick = { onSave("") },
                        modifier = Modifier.testTag("clear_call_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Clear call note",
                            tint = CallEndRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Remove note", color = CallEndRed)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(noteText.trim()) },
                modifier = Modifier.testTag("save_call_note_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_call_note_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
