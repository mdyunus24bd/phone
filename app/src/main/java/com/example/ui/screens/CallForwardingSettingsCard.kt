package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ContactEntity
import com.example.data.preferences.CallForwardingCondition
import com.example.data.preferences.CallForwardingPreferences
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent
import com.example.ui.theme.SimAccentEmerald

/**
 * Reusable Material Design 3 Call Forwarding settings card backed by DataStore.
 * Lets users toggle SIM call forwarding on/off, enter or pick a forwarding number,
 * select the forwarding condition, and persist changes to DataStore.
 */
@Composable
fun CallForwardingSettingsCard(
    preferences: CallForwardingPreferences,
    contacts: List<ContactEntity>,
    onToggleForwarding: (Boolean) -> Unit,
    onSaveForwarding: (
        enabled: Boolean,
        forwardingNumber: String,
        condition: CallForwardingCondition,
        dispatchCarrierMmi: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var enabledDraft by remember { mutableStateOf(preferences.enabled) }
    var numberDraft by remember { mutableStateOf(preferences.forwardingNumber) }
    var conditionDraft by remember { mutableStateOf(preferences.condition) }
    var numberError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(preferences) {
        enabledDraft = preferences.enabled
        numberDraft = preferences.forwardingNumber
        conditionDraft = preferences.condition
        numberError = null
    }

    val pickableContacts = remember(contacts) {
        contacts.filter { it.phoneNumber.isNotBlank() }.take(8)
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (enabledDraft && numberDraft.isNotBlank()) {
            BorderStroke(1.dp, SimAccentEmerald.copy(alpha = 0.55f))
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_forwarding_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Master Toggle
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
                        imageVector = Icons.Filled.PhoneForwarded,
                        contentDescription = "Call Forwarding",
                        tint = if (enabledDraft) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Call Forwarding",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (enabledDraft) {
                                    QualityExcellent.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ) {
                                Text(
                                    text = if (enabledDraft) "ACTIVE" else "OFF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (enabledDraft) QualityExcellent
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = if (enabledDraft && numberDraft.isNotBlank()) {
                                "${conditionDraft.label} → $numberDraft"
                            } else {
                                "Forward incoming SIM calls to another number"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = enabledDraft,
                    onCheckedChange = { next ->
                        enabledDraft = next
                        if (!next) {
                            numberError = null
                            onToggleForwarding(false)
                        } else if (numberDraft.isNotBlank()) {
                            onSaveForwarding(true, numberDraft.trim(), conditionDraft, false)
                        } else {
                            numberError = "Enter a forwarding number below, then tap Save"
                        }
                    },
                    modifier = Modifier.testTag("call_forwarding_toggle")
                )
            }

            // Forwarding Phone Number Input
            OutlinedTextField(
                value = numberDraft,
                onValueChange = {
                    numberDraft = it
                    numberError = null
                },
                label = { Text("Forwarding Phone Number") },
                placeholder = { Text("+1 555 019 2834") },
                leadingIcon = {
                    Icon(Icons.Filled.Call, contentDescription = null)
                },
                trailingIcon = {
                    if (numberDraft.isNotEmpty()) {
                        IconButton(onClick = { numberDraft = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear forwarding number"
                            )
                        }
                    }
                },
                isError = numberError != null,
                supportingText = {
                    if (numberError != null) {
                        Text(numberError!!)
                    } else {
                        val syncedNote = if (preferences.updatedAt > 0L) {
                            "Persisted in DataStore • ${formatTimestamp(preferences.updatedAt)}"
                        } else {
                            "Preferences are saved locally in DataStore"
                        }
                        Text(syncedNote)
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("call_forwarding_number_input")
            )

            // Quick-Pick from Saved Contacts
            AnimatedVisibility(visible = pickableContacts.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick Pick Contact",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pickableContacts, key = { it.id }) { contact ->
                            val destination = contact.phoneNumber
                            val selected = numberDraft.trim().equals(destination, ignoreCase = true)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    numberDraft = destination
                                    numberError = null
                                },
                                label = {
                                    Text(
                                        text = contact.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                        }
                    }
                }
            }

            // Forwarding Condition Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Forwarding Condition",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CallForwardingCondition.entries) { cond ->
                        FilterChip(
                            selected = conditionDraft == cond,
                            onClick = { conditionDraft = cond },
                            label = { Text(cond.label) },
                            modifier = Modifier.testTag("forwarding_condition_${cond.name}")
                        )
                    }
                }
            }

            // GSM MMI Preview for SIM Carrier Forwarding
            val mmiPreview = if (enabledDraft && numberDraft.isNotBlank()) {
                "${conditionDraft.mmiPrefix}${numberDraft.trim()}#"
            } else {
                conditionDraft.deactivateCode
            }
            Text(
                text = "Carrier GSM MMI code: $mmiPreview",
                style = MonoMetricStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val trimmed = numberDraft.trim()
                        if (enabledDraft && trimmed.isBlank()) {
                            numberError = "Enter a forwarding number before saving"
                        } else {
                            numberError = null
                            onSaveForwarding(enabledDraft, trimmed, conditionDraft, false)
                        }
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("save_call_forwarding_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }

                OutlinedButton(
                    onClick = {
                        val trimmed = numberDraft.trim()
                        if (enabledDraft && trimmed.isBlank()) {
                            numberError = "Enter a number to dial carrier MMI"
                        } else {
                            numberError = null
                            onSaveForwarding(enabledDraft, trimmed, conditionDraft, true)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("apply_carrier_mmi_button")
                ) {
                    Text("Dial MMI", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
