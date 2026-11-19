package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ContactEntity
import com.example.telecom.ConferenceParticipantSnapshot
import com.example.telecom.PhoneCountryResolver
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald

/**
 * Dialog allowing the user to dial a second caller (by phone number or contact pick)
 * during an active call, placing the current call on hold so they can merge into a conference.
 */
@Composable
fun AddConferenceCallerDialog(
    contacts: List<ContactEntity>,
    onDialAdditionalCaller: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var input by remember { mutableStateOf("") }

    val matchingContacts = remember(contacts, input) {
        val q = input.trim()
        val withNumbers = contacts.filter { it.phoneNumber.isNotBlank() }
        if (q.isEmpty()) {
            withNumbers.take(25)
        } else {
            val qDigits = q.filter { it.isDigit() }
            withNumbers.filter { c ->
                c.displayName.contains(q, ignoreCase = true) ||
                    c.phoneNumber.contains(q, ignoreCase = true) ||
                    (qDigits.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(qDigits))
            }.take(25)
        }
    }

    val dialTypedNumber: () -> Unit = {
        val clean = input.trim()
        if (clean.any { it.isDigit() }) {
            onDialAdditionalCaller(clean)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.AddIcCall,
                contentDescription = "Add caller to conference",
                tint = SimAccentEmerald
            )
        },
        title = {
            Text(
                text = "Add Call to Conference",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Your current call will be placed on hold while dialing. Tap Merge once connected to start the conference.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Phone number or search contact") },
                    placeholder = { Text("Enter number or contact name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { dialTypedNumber() }),
                    trailingIcon = {
                        if (input.isNotEmpty()) {
                            IconButton(onClick = { input = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear input")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("conference_add_number_input")
                )

                if (matchingContacts.isNotEmpty()) {
                    Text(
                        text = "Contacts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(matchingContacts, key = { it.id }) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onDialAdditionalCaller(contact.phoneNumber) }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                                    .testTag("conference_pick_contact_${contact.id}"),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ContactAvatar(
                                    name = contact.displayName,
                                    colorIndex = contact.avatarColorIndex,
                                    photoUri = contact.photoUri,
                                    size = 36.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = contact.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = contact.phoneNumber,
                                        style = MonoMetricStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call ${contact.displayName}",
                                    tint = SimAccentEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = dialTypedNumber,
                enabled = input.any { it.isDigit() },
                colors = ButtonDefaults.buttonColors(containerColor = SimAccentEmerald),
                modifier = Modifier.testTag("conference_dial_caller_button")
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Call")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog displaying all active participants in a carrier conference call with
 * per-participant Split (private call) and Disconnect controls.
 */
@Composable
fun ConferenceParticipantsDialog(
    participants: List<ConferenceParticipantSnapshot>,
    onSeparateParticipant: (String) -> Unit,
    onDisconnectParticipant: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Groups,
                contentDescription = "Conference participants",
                tint = SimAccentEmerald
            )
        },
        title = {
            Text(
                text = "Conference Call (${participants.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (participants.isEmpty()) {
                Text(
                    text = "Carrier conference call is active. Participant list will appear here when provided by your network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(participants, key = { it.callId }) { participant ->
                        val country = remember(participant.handle) {
                            PhoneCountryResolver.resolveCountryName(context, participant.handle)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("conference_participant_${participant.callId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ContactAvatar(
                                    name = participant.displayName,
                                    colorIndex = kotlin.math.abs(participant.handle.hashCode()) % 5,
                                    size = 38.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = participant.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            if (participant.displayName != participant.handle) {
                                                append(participant.handle)
                                                if (country.isNotBlank()) append(" • ")
                                            }
                                            if (country.isNotBlank()) append(country)
                                        }.ifBlank { participant.handle },
                                        style = MonoMetricStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (participant.canSeparate) {
                                    IconButton(
                                        onClick = { onSeparateParticipant(participant.callId) },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .testTag("conference_split_${participant.callId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.CallSplit,
                                            contentDescription = "Talk privately with ${participant.displayName}",
                                            tint = AccentSkyBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                if (participant.canDisconnect) {
                                    Surface(
                                        shape = CircleShape,
                                        color = CallEndRed.copy(alpha = 0.16f),
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            IconButton(
                                                onClick = { onDisconnectParticipant(participant.callId) },
                                                modifier = Modifier.testTag("conference_drop_${participant.callId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.CallEnd,
                                                    contentDescription = "Remove ${participant.displayName} from conference",
                                                    tint = CallEndRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
