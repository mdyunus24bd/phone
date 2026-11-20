package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.CallForwardingRuleEntity
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.data.local.SpamRuleEntity
import com.example.data.preferences.AppFontSizeOption
import com.example.data.preferences.AutoRecordMode
import com.example.data.preferences.CallForwardingCondition
import com.example.data.preferences.DialerSizeOption
import com.example.data.preferences.KeypadLayoutStyle
import com.example.data.preferences.VoxLineSettings
import com.example.security.CryptoVault
import com.example.telecom.NetworkQualitySample
import com.example.telecom.RingtoneOption
import com.example.telecom.SpamAssessment
import com.example.ui.components.EncryptionChip
import com.example.ui.components.MetricPill
import com.example.ui.components.QualityTrendChart
import com.example.ui.components.mosQualityColor
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent
import com.example.ui.theme.SimAccentEmerald
import com.example.ui.theme.ThemeMode

enum class ControlTab(val label: String, val icon: ImageVector) {
    RECORDING("Recording", Icons.Filled.FiberManualRecord),
    RINGTONES("Ringtones", Icons.Filled.NotificationsActive),
    ANALYTICS("Quality", Icons.Filled.GraphicEq),
    FORWARDING("Forwarding", Icons.Filled.PhoneForwarded),
    SPAM("Spam Shield", Icons.Filled.Shield),
    PRIVACY_THEME("Privacy & Theme", Icons.Filled.Palette)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenterScreen(
    selectedTab: ControlTab,
    onSelectTab: (ControlTab) -> Unit,
    onBack: () -> Unit,
    settings: VoxLineSettings,
    vaultStatus: CryptoVault.VaultStatus,
    recordingStorageFolder: String,
    contacts: List<ContactEntity>,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    availableRingtones: List<RingtoneOption>,
    previewingRingtoneUri: String?,
    callLogs: List<CallLogEntity>,
    forwardingRules: List<CallForwardingRuleEntity>,
    spamRules: List<SpamRuleEntity>,
    liveProbe: NetworkQualitySample?,
    isProbingQuality: Boolean,
    onToggleAutoRecord: (Boolean) -> Unit,
    onSelectAutoRecordMode: (AutoRecordMode) -> Unit,
    onAddAutoRecordNumber: (String) -> Unit,
    onRemoveAutoRecordNumber: (String) -> Unit,
    onClearAutoRecordNumbers: () -> Unit,
    onToggleRecordingPlayback: (CallRecordingEntity) -> Unit,
    onShareRecording: (CallRecordingEntity) -> Unit = {},
    onDeleteRecording: (CallRecordingEntity) -> Unit,
    onSelectRingtone: (String, String) -> Unit,
    onToggleRingtonePreview: (String, Boolean) -> Unit,
    onStopRingtonePreview: () -> Unit,
    onOpenSystemRingtonePicker: () -> Unit,
    onOpenCustomAudioRingtonePicker: () -> Unit,
    onSetVibrateSim: (Boolean) -> Unit,
    onResetRingtones: () -> Unit,
    onRunLiveProbe: () -> Unit,
    onToggleCallForwarding: (Boolean) -> Unit,
    onSaveCallForwarding: (Boolean, String, CallForwardingCondition, Boolean) -> Unit,
    onApplyForwardingRule: (CallForwardingRuleEntity, String, Boolean, Boolean) -> Unit,
    onCancelAllCarrierForwarding: () -> Unit,
    onAddQuickReplyTemplate: (String) -> Unit,
    onRemoveQuickReplyTemplate: (String) -> Unit,
    onResetQuickReplyTemplates: () -> Unit,
    onToggleSpamShield: (Boolean) -> Unit,
    onToggleAutoBlock: (Boolean) -> Unit,
    onOpenBlockedNumbers: () -> Unit,
    onAddSpamRule: (String, String, Int, Boolean) -> Unit,
    onDeleteSpamRule: (SpamRuleEntity) -> Unit,
    onCheckSpamNumber: (String) -> SpamAssessment,
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
    onWipeVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("settings_control_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Banner using generated telecom acoustic shield illustration
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(136.dp)
                            .clip(RoundedCornerShape(22.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_security_shield_banner_1795059063429),
                            contentDescription = "VoxLine encryption and call telemetry banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xE6070E18), Color(0x99070E18), Color(0x55070E18))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            EncryptionChip(label = "AES-256-GCM • Keystore")
                            Column {
                                Text(
                                    text = "Network & Privacy Control",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Text(
                                    text = "SIM Quality Telemetry • Call Forwarding • Spam Shield",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }

                // Dedicated Blocked Numbers Settings Entry Card
                item {
                    val blockedCount = remember(spamRules) { spamRules.count { it.autoBlock } }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable(onClick = onOpenBlockedNumbers)
                            .testTag("open_blocked_numbers_settings_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(CallEndRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Block,
                                    contentDescription = "Blocked Numbers",
                                    tint = CallEndRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Blocked Numbers",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (blockedCount == 0) {
                                        "Manage numbers to automatically reject incoming calls"
                                    } else {
                                        "$blockedCount ${if (blockedCount == 1) "number" else "numbers"} blocked • Auto-reject active"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Blocked Numbers",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Quick Entry Card for Font Size & Dialer Size
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onSelectTab(ControlTab.PRIVACY_THEME) }
                            .testTag("open_font_dialer_size_settings_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatSize,
                                    contentDescription = "Font and Dialer Size",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Font & Dialer Size",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Font: ${settings.fontSizeOption.label} (${settings.fontSizeOption.percentLabel}) • Dialer: ${settings.dialerSizeOption.label}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Font and Dialer Size settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Section Selector Chips
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ControlTab.entries) { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { onSelectTab(tab) },
                                label = { Text(tab.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("control_tab_${tab.name}")
                            )
                        }
                    }
                }

                item {
                    when (selectedTab) {
                        ControlTab.RECORDING -> CallRecordingSection(
                            settings = settings,
                            storageFolderLabel = recordingStorageFolder,
                            contacts = contacts,
                            recordings = recordings,
                            playingRecordingId = playingRecordingId,
                            onToggleAutoRecord = onToggleAutoRecord,
                            onSelectAutoRecordMode = onSelectAutoRecordMode,
                            onAddAutoRecordNumber = onAddAutoRecordNumber,
                            onRemoveAutoRecordNumber = onRemoveAutoRecordNumber,
                            onClearAutoRecordNumbers = onClearAutoRecordNumbers,
                            onTogglePlayback = onToggleRecordingPlayback,
                            onShareRecording = onShareRecording,
                            onDeleteRecording = onDeleteRecording
                        )
                        ControlTab.RINGTONES -> RingtoneSettingsSection(
                            ringtones = settings.ringtones,
                            availableRingtones = availableRingtones,
                            previewingUri = previewingRingtoneUri,
                            onSelectRingtone = onSelectRingtone,
                            onTogglePreview = onToggleRingtonePreview,
                            onStopPreview = onStopRingtonePreview,
                            onOpenSystemPicker = onOpenSystemRingtonePicker,
                            onOpenCustomAudioPicker = onOpenCustomAudioRingtonePicker,
                            onSetVibrateSim = onSetVibrateSim,
                            onResetDefaults = onResetRingtones
                        )
                        ControlTab.ANALYTICS -> QualityAnalyticsSection(
                            callLogs = callLogs,
                            liveProbe = liveProbe,
                            isProbingQuality = isProbingQuality,
                            onRunLiveProbe = onRunLiveProbe
                        )
                        ControlTab.FORWARDING -> CallForwardingSection(
                            callForwardingPreferences = settings.callForwarding,
                            contacts = contacts,
                            forwardingRules = forwardingRules,
                            onToggleCallForwarding = onToggleCallForwarding,
                            onSaveCallForwarding = onSaveCallForwarding,
                            onApplyForwardingRule = onApplyForwardingRule,
                            onCancelAllCarrierForwarding = onCancelAllCarrierForwarding,
                            quickReplyTemplates = settings.quickReplyTemplates,
                            onAddQuickReplyTemplate = onAddQuickReplyTemplate,
                            onRemoveQuickReplyTemplate = onRemoveQuickReplyTemplate,
                            onResetQuickReplyTemplates = onResetQuickReplyTemplates
                        )
                        ControlTab.SPAM -> SpamShieldSection(
                            settings = settings,
                            spamRules = spamRules,
                            onToggleSpamShield = onToggleSpamShield,
                            onToggleAutoBlock = onToggleAutoBlock,
                            onOpenBlockedNumbers = onOpenBlockedNumbers,
                            onAddSpamRule = onAddSpamRule,
                            onDeleteSpamRule = onDeleteSpamRule,
                            onCheckSpamNumber = onCheckSpamNumber
                        )
                        ControlTab.PRIVACY_THEME -> PrivacySyncAndThemeSection(
                            settings = settings,
                            vaultStatus = vaultStatus,
                            onSetThemeMode = onSetThemeMode,
                            onSetAccentPalette = onSetAccentPalette,
                            onSetHighContrast = onSetHighContrast,
                            onSetFontSizeOption = onSetFontSizeOption,
                            onSetDialerSizeOption = onSetDialerSizeOption,
                            onSetKeypadLayout = onSetKeypadLayout,
                            onSetScreenPrivacy = onSetScreenPrivacy,
                            onSetDtmfTones = onSetDtmfTones,
                            onRequestExportSync = onRequestExportSync,
                            onRequestImportSync = onRequestImportSync,
                            onWipeVault = onWipeVault
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityAnalyticsSection(
    callLogs: List<CallLogEntity>,
    liveProbe: NetworkQualitySample?,
    isProbingQuality: Boolean,
    onRunLiveProbe: () -> Unit
) {
    val measuredLogs = remember(callLogs) { callLogs.filter { it.mosScore > 0f } }
    val mosValues = remember(measuredLogs, liveProbe) {
        val base = measuredLogs.take(15).reversed().map { it.mosScore }
        if (liveProbe != null) base + liveProbe.mosScore else base
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Live Network Quality Probe Card
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
                        Text("Live SIM Voice Diagnostic", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = liveProbe?.let {
                                "${it.transportLabel} • ${it.codecLabel} • ↓${it.downstreamKbps / 1000}Mbps"
                            } ?: "Run a real-time RTT jitter probe to measure voice MOS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onRunLiveProbe,
                        enabled = !isProbingQuality,
                        modifier = Modifier.testTag("run_quality_probe_button")
                    ) {
                        if (isProbingQuality) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Probe")
                    }
                }

                liveProbe?.let { sample ->
                    val color = mosQualityColor(sample.mosScore)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricPill("MOS", "%.2f".format(sample.mosScore), color, Modifier.weight(1f))
                        MetricPill("RTT", "${sample.latencyMs}ms", AccentSkyBlue, Modifier.weight(1f))
                        MetricPill("JITTER", "${sample.jitterMs}ms", QualityExcellent, Modifier.weight(1f))
                        MetricPill(
                            "LOSS",
                            "%.1f%%".format(sample.packetLossPct),
                            if (sample.packetLossPct > 2f) CallEndRed else MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Historical Call Quality MOS Trend Chart
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Call Quality MOS Trend (ITU-T G.107)", style = MaterialTheme.typography.titleMedium)
                if (mosValues.isEmpty()) {
                    Text(
                        text = "Complete a SIM call or run a Live Voice Diagnostic above to populate real-time MOS voice quality trends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    QualityTrendChart(
                        values = mosValues,
                        lineColor = mosQualityColor(mosValues.last()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    )
                    Text(
                        text = "Latest MOS: ${"%.2f".format(mosValues.last())} • Samples: ${mosValues.size}",
                        style = MonoMetricStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // SIM Cellular Call Quality Summary
        NetworkSummaryCard(
            title = "SIM Cellular Voice Summary",
            count = measuredLogs.size,
            avgMos = if (measuredLogs.isNotEmpty()) measuredLogs.map { it.mosScore }.average().toFloat() else 0f,
            avgRtt = if (measuredLogs.isNotEmpty()) measuredLogs.map { it.avgLatencyMs }.average().toInt() else 0,
            accent = SimAccentEmerald,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NetworkSummaryCard(
    title: String,
    count: Int,
    avgMos: Float,
    avgRtt: Int,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(
                text = if (avgMos > 0f) "MOS ${"%.2f".format(avgMos)}" else "No calls yet",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "$count measured • ${if (avgRtt > 0) "${avgRtt}ms avg RTT" else "--"}",
                style = MonoMetricStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
