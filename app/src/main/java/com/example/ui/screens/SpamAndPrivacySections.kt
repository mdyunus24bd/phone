package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.data.local.SpamRuleEntity
import com.example.data.preferences.AppFontSizeOption
import com.example.data.preferences.DialerSizeOption
import com.example.data.preferences.KeypadLayoutStyle
import com.example.data.preferences.VoxLineSettings
import com.example.security.CryptoVault
import com.example.telecom.SpamAssessment
import com.example.ui.components.SpamRiskChip
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent
import com.example.ui.theme.ThemeMode

@Composable
fun SpamShieldSection(
    settings: VoxLineSettings,
    spamRules: List<SpamRuleEntity>,
    onToggleSpamShield: (Boolean) -> Unit,
    onToggleAutoBlock: (Boolean) -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onAddSpamRule: (String, String, Int, Boolean) -> Unit,
    onDeleteSpamRule: (SpamRuleEntity) -> Unit,
    onCheckSpamNumber: (String) -> SpamAssessment
) {
    var testQuery by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<SpamAssessment?>(null) }
    var newPattern by remember { mutableStateOf("") }
    var newLabel by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                        Text("Real-Time Spam Shield", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Screen SIM numbers against heuristics and custom blocklist rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.spamShieldEnabled,
                        onCheckedChange = onToggleSpamShield,
                        modifier = Modifier.testTag("toggle_spam_shield")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Block High-Risk Callers", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Reject and log calls matching blocked spam rules",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoBlockHighRiskSpam,
                        onCheckedChange = onToggleAutoBlock
                    )
                }
                OutlinedButton(
                    onClick = onOpenBlockedNumbers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spam_section_manage_blocked_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = null,
                        tint = CallEndRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Manage Blocked Numbers (${spamRules.count { it.autoBlock }})")
                }
            }
        }

        // Interactive Spam Risk Inspector
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Inspect Phone Number", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = testQuery,
                        onValueChange = { testQuery = it },
                        placeholder = { Text("+19005550199") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("spam_check_input")
                    )
                    FilledTonalButton(
                        onClick = { testResult = onCheckSpamNumber(testQuery) },
                        modifier = Modifier.testTag("run_spam_check_button")
                    ) {
                        Text("Scan")
                    }
                }
                testResult?.let { res ->
                    SpamRiskChip(level = res.level, score = res.score)
                    Text(
                        text = res.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Add Custom Block / Prefix Rule
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Add Blocklist or Prefix Rule", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = newPattern,
                    onValueChange = { newPattern = it },
                    label = { Text("Exact Number or Prefix* (e.g. +1800*)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spam_pattern_input")
                )
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text("Category Label (e.g. Telemarketer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("spam_label_input")
                )
                Button(
                    onClick = {
                        onAddSpamRule(newPattern, newLabel, 95, true)
                        newPattern = ""
                        newLabel = ""
                    },
                    enabled = newPattern.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_spam_rule_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Spam Rule")
                }
            }
        }

        // Active Rules List
        Text(
            text = "Active Spam Rules (${spamRules.size})",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (spamRules.isEmpty()) {
            Text(
                text = "Built-in heuristics are active. Add custom numbers or wildcard prefixes above to permanently block callers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            spamRules.forEach { rule ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.pattern, style = MonoMetricStyle, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${rule.label} • Risk ${rule.riskScore} • Matches: ${rule.matchCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeleteSpamRule(rule) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete spam rule")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacySyncAndThemeSection(
    settings: VoxLineSettings,
    vaultStatus: CryptoVault.VaultStatus,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAccentPalette: (AccentPalette) -> Unit,
    onSetHighContrast: (Boolean) -> Unit,
    onSetFontSizeOption: (AppFontSizeOption) -> Unit,
    onSetDialerSizeOption: (DialerSizeOption) -> Unit,
    onSetKeypadLayout: (KeypadLayoutStyle) -> Unit,
    onSetScreenPrivacy: (Boolean) -> Unit,
    onSetDtmfTones: (Boolean) -> Unit,
    onRequestExportSync: (String) -> Unit,
    onRequestImportSync: (String) -> Unit,
    onWipeVault: () -> Unit
) {
    var syncPassphrase by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Font Size & Dialer Size Controls Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_font_dialer_size_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Font Size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Adjust text size across Recents, Contacts, and Settings",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = settings.fontSizeOption.percentLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AppFontSizeOption.entries) { option ->
                        FilterChip(
                            selected = settings.fontSizeOption == option,
                            onClick = { onSetFontSizeOption(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("settings_font_size_${option.name}")
                        )
                    }
                }

                // Live Font Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Preview • Incoming Call",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Alex Morgan",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "+1 555-019-2834 • SIM 1 • Just now",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dialpad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dialer Size", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = settings.dialerSizeOption.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DialerSizeOption.entries) { option ->
                        FilterChip(
                            selected = settings.dialerSizeOption == option,
                            onClick = { onSetDialerSizeOption(option) },
                            label = { Text(option.label) },
                            modifier = Modifier.testTag("settings_dialer_size_${option.name}")
                        )
                    }
                }

                // Live Dialer Key Size Preview
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val previewKeySize = (46f * settings.dialerSizeOption.scale).dp.coerceIn(38.dp, 62.dp)
                    val previewFontSize = (18f * settings.dialerSizeOption.scale).sp
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("1" to "∞", "2" to "ABC", "3" to "DEF").forEach { (digit, letters) ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.size(previewKeySize)
                            ) {
                                Column(
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = digit,
                                        fontSize = previewFontSize,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = letters,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Customizable Dark Mode Studio
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Light & Dark Theme Appearance", style = MaterialTheme.typography.titleMedium)
                }

                Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            label = { Text(mode.label) },
                            leadingIcon = when (mode) {
                                ThemeMode.LIGHT -> {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.LightMode,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                ThemeMode.DARK, ThemeMode.AMOLED -> {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.DarkMode,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                ThemeMode.SYSTEM -> null
                            },
                            modifier = Modifier.testTag("theme_mode_${mode.name}")
                        )
                    }
                }

                Text("Accent Palette", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AccentPalette.entries.forEach { palette ->
                        val selected = settings.accentPalette == palette
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSetAccentPalette(palette) }
                                .padding(6.dp)
                                .testTag("accent_${palette.name}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(palette.previewColor)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = palette.label.substringBefore(" "),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("High-Contrast Accessibility", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Boost foreground contrast and outline visibility",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = settings.highContrast, onCheckedChange = onSetHighContrast)
                }

                Text("Keypad Layout", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(KeypadLayoutStyle.entries) { layout ->
                        FilterChip(
                            selected = settings.keypadLayout == layout,
                            onClick = { onSetKeypadLayout(layout) },
                            label = { Text(layout.label) },
                            modifier = Modifier.testTag("settings_keypad_layout_${layout.name}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dialpad DTMF Tones", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = settings.dtmfTonesEnabled, onCheckedChange = onSetDtmfTones)
                }
            }
        }

        // Cross-Device Encrypted Sync Vault
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Encrypted Cross-Device Sync Vault", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Local AES-256-GCM sync bundles (cloud auto-sync disabled)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = if (settings.lastSyncTimestamp > 0L) {
                        "Last sync: ${formatTimestamp(settings.lastSyncTimestamp)} • SHA-256 #${settings.lastSyncChecksum}"
                    } else {
                        "Export an encrypted .vxync snapshot of call logs, contacts, and spam rules to restore on another device."
                    },
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = syncPassphrase,
                    onValueChange = { syncPassphrase = it },
                    label = { Text("Sync Encryption Passphrase (min 6 chars)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sync_passphrase_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onRequestExportSync(syncPassphrase) },
                        enabled = syncPassphrase.length >= 6,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_sync_bundle_button")
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { onRequestImportSync(syncPassphrase) },
                        enabled = syncPassphrase.length >= 6,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_sync_bundle_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }
        }

        // Privacy & Keystore Encryption Standards
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = QualityExcellent)
                    Text("Privacy & Encryption Standards", style = MaterialTheme.typography.titleMedium)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("Cipher: ${vaultStatus.cipherSuite}", style = MonoMetricStyle, color = QualityExcellent)
                        Text(
                            "Storage: ${if (vaultStatus.keyStoreBacked) "Android Hardware Keystore" else "In-Memory Fallback"}",
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Key Fingerprint: ${vaultStatus.keyFingerprint}",
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Screen Privacy (FLAG_SECURE)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Block screenshots & hide app preview in recents",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.screenPrivacySecure,
                        onCheckedChange = onSetScreenPrivacy,
                        modifier = Modifier.testTag("toggle_flag_secure")
                    )
                }

                OutlinedButton(
                    onClick = { confirmWipe = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CallEndRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Wipe Local Encrypted Vault")
                }
            }
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wipe Encrypted Vault?") },
            text = {
                Text("This permanently deletes all stored call logs, encrypted recordings, contacts, and spam rules on this device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmWipe = false
                        onWipeVault()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CallEndRed)
                ) {
                    Text("Erase All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWipe = false }) { Text("Cancel") }
            }
        )
    }
}
