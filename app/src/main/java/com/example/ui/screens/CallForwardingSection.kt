package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallForwardingRuleEntity
import com.example.data.local.ContactEntity
import com.example.data.preferences.CallForwardingCondition
import com.example.data.preferences.CallForwardingPreferences
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent

@Composable
fun CallForwardingSection(
    callForwardingPreferences: CallForwardingPreferences,
    contacts: List<ContactEntity>,
    forwardingRules: List<CallForwardingRuleEntity>,
    onToggleCallForwarding: (Boolean) -> Unit,
    onSaveCallForwarding: (Boolean, String, CallForwardingCondition, Boolean) -> Unit,
    onApplyForwardingRule: (CallForwardingRuleEntity, String, Boolean, Boolean) -> Unit,
    onCancelAllCarrierForwarding: () -> Unit,
    quickReplyTemplates: List<String>,
    onAddQuickReplyTemplate: (String) -> Unit,
    onRemoveQuickReplyTemplate: (String) -> Unit,
    onResetQuickReplyTemplates: () -> Unit,
    modifier: Modifier = Modifier
) {
    var editingRule by remember { mutableStateOf<CallForwardingRuleEntity?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Primary Call Forwarding Card (DataStore + GSM MMI)
        CallForwardingSettingsCard(
            preferences = callForwardingPreferences,
            contacts = contacts,
            onToggleForwarding = onToggleCallForwarding,
            onSaveForwarding = onSaveCallForwarding
        )

        // Carrier GSM MMI Conditional Rules
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("carrier_forwarding_rules_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneForwarded,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "Carrier GSM MMI Forwarding Rules",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Configure individual SIM carrier conditional forwarding codes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                forwardingRules.forEach { rule ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("forwarding_rule_${rule.conditionKey}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = rule.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (rule.enabled) {
                                            QualityExcellent.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        }
                                    ) {
                                        Text(
                                            text = if (rule.enabled) "ON" else "OFF",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (rule.enabled) {
                                                QualityExcellent
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (rule.enabled && rule.targetDestination.isNotBlank()) {
                                        "Forward to ${rule.targetDestination} (${rule.activatePrefix}${rule.targetDestination}#)"
                                    } else {
                                        "Inactive • Deactivate code ${rule.deactivateCode}"
                                    },
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(
                                onClick = { editingRule = rule },
                                modifier = Modifier.testTag("edit_forwarding_rule_${rule.conditionKey}")
                            ) {
                                Text("Configure")
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCancelAllCarrierForwarding,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CallEndRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("cancel_all_carrier_forwarding_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel All Carrier Forwarding (##002#)")
                }
            }
        }

        // Predefined Incoming Call Quick Reply SMS Templates
        QuickReplyTemplatesCard(
            templates = quickReplyTemplates,
            onAddTemplate = onAddQuickReplyTemplate,
            onRemoveTemplate = onRemoveQuickReplyTemplate,
            onResetDefaults = onResetQuickReplyTemplates
        )
    }

    editingRule?.let { rule ->
        ForwardingRuleDialog(
            rule = rule,
            onDismiss = { editingRule = null },
            onSave = { destination, enabled, dispatchCarrierMmi ->
                onApplyForwardingRule(rule, destination, enabled, dispatchCarrierMmi)
                editingRule = null
            }
        )
    }
}

@Composable
private fun ForwardingRuleDialog(
    rule: CallForwardingRuleEntity,
    onDismiss: () -> Unit,
    onSave: (destination: String, enabled: Boolean, dispatchCarrierMmi: Boolean) -> Unit
) {
    var destination by remember(rule.conditionKey) { mutableStateOf(rule.targetDestination) }
    var enabled by remember(rule.conditionKey) { mutableStateOf(rule.enabled) }
    var destinationError by remember { mutableStateOf<String?>(null) }

    val mmiPreview = if (enabled && destination.isNotBlank()) {
        "${rule.activatePrefix}${destination.trim()}#"
    } else {
        rule.deactivateCode
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rule.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Forwarding Rule", style = MaterialTheme.typography.titleSmall)
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            destinationError = null
                        },
                        modifier = Modifier.testTag("dialog_forwarding_rule_toggle")
                    )
                }

                OutlinedTextField(
                    value = destination,
                    onValueChange = {
                        destination = it
                        destinationError = null
                    },
                    label = { Text("Destination Phone Number") },
                    placeholder = { Text("+1 555 019 2834") },
                    leadingIcon = { Icon(Icons.Filled.Call, contentDescription = null) },
                    isError = destinationError != null,
                    supportingText = {
                        if (destinationError != null) {
                            Text(destinationError!!)
                        } else {
                            Text("GSM MMI Code: $mmiPreview", style = MonoMetricStyle)
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_forwarding_destination_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = destination.trim()
                    if (enabled && trimmed.isBlank()) {
                        destinationError = "Enter a forwarding number"
                    } else {
                        onSave(trimmed, enabled, true)
                    }
                },
                modifier = Modifier.testTag("dialog_forwarding_save_dial_button")
            ) {
                Text("Save & Dial MMI")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        val trimmed = destination.trim()
                        if (enabled && trimmed.isBlank()) {
                            destinationError = "Enter a forwarding number"
                        } else {
                            onSave(trimmed, enabled, false)
                        }
                    },
                    modifier = Modifier.testTag("dialog_forwarding_save_only_button")
                ) {
                    Text("Save Only")
                }
            }
        }
    )
}
