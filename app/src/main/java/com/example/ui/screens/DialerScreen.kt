package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.preferences.AutoRecordMode
import com.example.data.preferences.KeypadLayoutStyle
import com.example.telecom.PhoneCountryResolver
import com.example.telecom.SimSlotInfo
import com.example.telecom.SpamAssessment
import com.example.telecom.SpamRiskLevel
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SpamRiskChip
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald
import com.example.ui.theme.ThemeMode

private data class DialKey(val main: String, val letters: String)

private val DialKeys = listOf(
    listOf(DialKey("1", "∞"), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*", "+"), DialKey("0", "+"), DialKey("#", ""))
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(
    dialInput: String,
    selectedSimIndex: Int,
    activeSims: List<SimSlotInfo>,
    contacts: List<ContactEntity>,
    callLogs: List<CallLogEntity> = emptyList(),
    isDefaultDialer: Boolean,
    hasPhoneStatePermission: Boolean,
    spamAssessment: SpamAssessment?,
    themeMode: ThemeMode,
    keypadLayout: KeypadLayoutStyle,
    autoRecordEnabled: Boolean,
    autoRecordMode: AutoRecordMode,
    autoRecordSpecificCount: Int,
    onDigitClick: (Char) -> Unit,
    onSetDialInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSelectSimIndex: (Int) -> Unit,
    onSelectKeypadLayout: (KeypadLayoutStyle) -> Unit,
    onToggleLightDarkTheme: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onOpenPhoneSimSettings: () -> Unit,
    onPlaceCall: (String) -> Unit,
    onAddNewContact: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showLayoutMenu by remember { mutableStateOf(false) }

    val dialedCountry = remember(dialInput) {
        if (dialInput.filter { it.isDigit() }.length >= 2) {
            PhoneCountryResolver.resolveCountryName(context, dialInput)
        } else {
            ""
        }
    }

    val contactsById = remember(contacts) { contacts.associateBy { it.id } }
    val contactsByDigits = remember(contacts) {
        contacts.mapNotNull { c ->
            val d = c.phoneNumber.filter { it.isDigit() }
            if (d.isNotEmpty()) d to c else null
        }.toMap()
    }

    val displayedRecentLogs = remember(callLogs, dialInput, contactsById, contactsByDigits) {
        val query = dialInput.trim()
        val digits = query.filter { it.isDigit() }
        if (query.isEmpty()) {
            callLogs.take(12)
        } else {
            callLogs.filter { log ->
                val logDigits = log.phoneNumber.filter { it.isDigit() }
                val matched = log.contactInfo.contactId?.let { contactsById[it] } ?: contactsByDigits[logDigits]
                val name = matched?.displayName?.ifBlank { log.resolvedContactName } ?: log.resolvedContactName
                name.contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() && logDigits.contains(digits))
            }.take(10)
        }
    }

    val matchedContacts = remember(dialInput, contacts) {
        val query = dialInput.trim()
        if (query.isEmpty()) {
            emptyList()
        } else {
            val digits = query.filter { it.isDigit() }
            contacts.filter { c ->
                c.phoneNumber.isNotBlank() && (
                    c.displayName.contains(query, ignoreCase = true) ||
                        (digits.isNotEmpty() && c.phoneNumber.filter { it.isDigit() }.contains(digits))
                    )
            }.take(5)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("keypad_fullscreen_page"),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("close_keypad_popup_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Keypad",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = keypadLayout.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // Quick Auto-Record Status Pill -> Jumps to Recording Settings
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = if (autoRecordEnabled) {
                            CallEndRed.copy(alpha = 0.16f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = BorderStroke(
                            1.dp,
                            if (autoRecordEnabled) CallEndRed.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenRecordingSettings)
                            .testTag("dialer_auto_record_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FiberManualRecord,
                                contentDescription = "Automatic Call Recording Settings",
                                tint = if (autoRecordEnabled) CallEndRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = when {
                                    !autoRecordEnabled -> "Rec Off"
                                    autoRecordMode == AutoRecordMode.SPECIFIC_NUMBERS -> "Auto ($autoRecordSpecificCount)"
                                    else -> "Auto All"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoRecordEnabled) CallEndRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Keypad Layout Switcher Menu
                    Box {
                        IconButton(
                            onClick = { showLayoutMenu = true },
                            modifier = Modifier.testTag("dialer_layout_switcher_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GridView,
                                contentDescription = "Change keypad layout",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showLayoutMenu,
                            onDismissRequest = { showLayoutMenu = false }
                        ) {
                            KeypadLayoutStyle.entries.forEach { style ->
                                val isSelected = keypadLayout == style
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = style.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = style.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        showLayoutMenu = false
                                        onSelectKeypadLayout(style)
                                    },
                                    modifier = Modifier.testTag("keypad_layout_option_${style.name}")
                                )
                            }
                        }
                    }

                    // Quick 1-Tap Light / Dark Theme Switcher
                    val isLightTheme = themeMode == ThemeMode.LIGHT
                    IconButton(
                        onClick = onToggleLightDarkTheme,
                        modifier = Modifier.testTag("dialer_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isLightTheme) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = if (isLightTheme) "Switch to Dark Theme" else "Switch to Light Theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag("dialer_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val compactHeight = maxHeight < 580.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 560.dp)
                    .then(if (compactHeight) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Upside: Recent Calls List (scrollable above the keypad)
                Column(
                    modifier = if (compactHeight) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 170.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dialInput.isEmpty()) "Recent Calls" else "Matching Calls",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (displayedRecentLogs.isNotEmpty()) {
                            Text(
                                text = "${displayedRecentLogs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Matched Contacts Quick Strip when dialing digits
                    AnimatedVisibility(visible = matchedContacts.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchedContacts, key = { "match_${it.id}" }) { contact ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSetDialInput(contact.phoneNumber) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ContactAvatar(
                                            name = contact.displayName,
                                            colorIndex = contact.avatarColorIndex,
                                            photoUri = contact.photoUri,
                                            size = 28.dp
                                        )
                                        Column {
                                            Text(
                                                text = contact.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = contact.phoneNumber,
                                                style = MonoMetricStyle,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (displayedRecentLogs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = !compactHeight)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (dialInput.isEmpty()) "No recent calls yet" else "No matching recent calls",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (!compactHeight) Modifier.weight(1f) else Modifier)
                                .testTag("keypad_recent_calls_list"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 2.dp)
                        ) {
                            items(displayedRecentLogs, key = { "kp_recent_${it.id}" }) { log ->
                                val logDigits = log.phoneNumber.filter { it.isDigit() }
                                val matched = log.contactInfo.contactId?.let { contactsById[it] }
                                    ?: contactsByDigits[logDigits]
                                val country = remember(log.phoneNumber) {
                                    PhoneCountryResolver.resolveCountryName(context, log.phoneNumber)
                                }
                                KeypadRecentCallRow(
                                    log = log,
                                    matchedContact = matched,
                                    callingCountry = country,
                                    onFillNumber = { onSetDialInput(log.phoneNumber) },
                                    onCallNow = { onPlaceCall(log.phoneNumber) }
                                )
                            }
                        }
                    }
                }

                // Dial Number Display Area (clean, no SIM badge)
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Spacer(Modifier.size(48.dp))
                            Text(
                                text = dialInput.ifEmpty { "" },
                                style = if (dialInput.length > 13) {
                                    MaterialTheme.typography.headlineMedium
                                } else {
                                    MaterialTheme.typography.displaySmall
                                },
                                color = if (dialInput.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .testTag("dial_display_text")
                            )
                            IconButton(
                                onClick = onBackspace,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("dial_backspace_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete digit",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Live resolved calling country name for typed number
                        AnimatedVisibility(visible = dialedCountry.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("dial_country_label")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = "Calling country",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = dialedCountry,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Real-time Spam Assessment indicator for typed number
                        AnimatedVisibility(visible = spamAssessment != null && spamAssessment.level != SpamRiskLevel.SAFE) {
                            spamAssessment?.let { info ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SpamRiskChip(level = info.level, score = info.score)
                                    Text(
                                        text = info.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Switchable Keypad Layouts (Modern Circular / Compact Grid / One-Handed Right / One-Handed Left)
                AnimatedContent(
                    targetState = keypadLayout,
                    label = "keypad_layout_transition"
                ) { layout ->
                    when (layout) {
                        KeypadLayoutStyle.CIRCULAR_MODERN -> CircularModernKeypad(
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitClick('+')
                            }
                        )

                        KeypadLayoutStyle.COMPACT_TACTILE -> CompactTactileKeypad(
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitClick('+')
                            }
                        )

                        KeypadLayoutStyle.ONE_HANDED_RIGHT -> OneHandedKeypad(
                            alignRight = true,
                            dialInputNotEmpty = dialInput.isNotEmpty(),
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitClick('+')
                            },
                            onSwapHand = { onSelectKeypadLayout(KeypadLayoutStyle.ONE_HANDED_LEFT) },
                            onBackspace = onBackspace,
                            onClear = onClear
                        )

                        KeypadLayoutStyle.ONE_HANDED_LEFT -> OneHandedKeypad(
                            alignRight = false,
                            dialInputNotEmpty = dialInput.isNotEmpty(),
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDigitClick(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDigitClick('+')
                            },
                            onSwapHand = { onSelectKeypadLayout(KeypadLayoutStyle.ONE_HANDED_RIGHT) },
                            onBackspace = onBackspace,
                            onClear = onClear
                        )
                    }
                }

                // Bottom Action Row: Add Contact | Call Icon Button (Icon Only) | Clear
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable { onAddNewContact(dialInput.trim()) }
                            .testTag("dialer_add_contact_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add new contact",
                                tint = if (dialInput.isNotEmpty()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Clean Primary Call Icon-Only Action Button
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(listOf(CallAcceptGreen, SimAccentEmerald))
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlaceCall(dialInput)
                            }
                            .testTag("place_call_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Call",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable(enabled = dialInput.isNotEmpty()) { onClear() }
                            .testTag("clear_dial_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "CLR",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (dialInput.isNotEmpty()) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadRecentCallRow(
    log: CallLogEntity,
    matchedContact: ContactEntity?,
    callingCountry: String,
    onFillNumber: () -> Unit,
    onCallNow: () -> Unit
) {
    val isMissed = log.direction == CallDirection.MISSED
    val dirIcon = when (log.direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        CallDirection.BLOCKED -> Icons.Filled.Block
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
    }
    val dirColor = when (log.direction) {
        CallDirection.MISSED, CallDirection.BLOCKED -> CallEndRed
        CallDirection.OUTGOING -> AccentSkyBlue
        CallDirection.INCOMING -> CallAcceptGreen
    }
    val resolvedName = matchedContact?.displayName?.ifBlank { log.resolvedContactName } ?: log.resolvedContactName

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isMissed) BorderStroke(1.dp, CallEndRed.copy(alpha = 0.25f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onFillNumber)
            .testTag("keypad_recent_row_${log.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = dirColor.copy(alpha = 0.14f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = dirIcon,
                        contentDescription = log.direction.name,
                        tint = dirColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = resolvedName.ifBlank { log.phoneNumber },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isMissed) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isMissed) CallEndRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        if (isMissed) append("Missed • ")
                        if (resolvedName.isNotBlank()) append("${log.phoneNumber} • ")
                        if (callingCountry.isNotBlank()) append("$callingCountry • ")
                        append(formatTimestamp(log.timestamp))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMissed) CallEndRed.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCallNow,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("keypad_recent_call_btn_${log.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = "Call ${resolvedName.ifBlank { log.phoneNumber }}",
                    tint = CallAcceptGreen,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CircularModernKeypad(
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("keypad_circular_modern"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DialKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isSymbol = key.main == "*" || key.main == "#"
                    Surface(
                        shape = CircleShape,
                        color = if (isSymbol) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        ),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .size(74.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onKeyPress(key.main.first()) },
                                onLongClick = { if (key.main == "0") onZeroLongPress() }
                            )
                            .testTag("dial_key_${key.main}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = key.main,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTactileKeypad(
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keypad_compact_tactile")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { onKeyPress(key.main.first()) },
                                    onLongClick = { if (key.main == "0") onZeroLongPress() }
                                )
                                .testTag("dial_key_${key.main}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = key.main,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (key.letters.isNotEmpty()) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = key.letters,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OneHandedKeypad(
    alignRight: Boolean,
    dialInputNotEmpty: Boolean,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit,
    onSwapHand: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val sideRail: @Composable () -> Unit = {
        Column(
            modifier = Modifier.width(64.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSwapHand)
                    .testTag("one_handed_swap_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Switch keypad hand side",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = dialInputNotEmpty, onClick = onBackspace)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = if (dialInputNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = dialInputNotEmpty, onClick = onClear)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear number",
                        tint = if (dialInputNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }
        }
    }

    val keyCluster: @Composable (Modifier) -> Unit = { clusterModifier ->
        Column(
            modifier = clusterModifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DialKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onKeyPress(key.main.first()) },
                                    onLongClick = { if (key.main == "0") onZeroLongPress() }
                                )
                                .testTag("dial_key_${key.main}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = key.main,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (key.letters.isNotEmpty()) {
                                    Text(
                                        text = key.letters,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (alignRight) "keypad_one_handed_right" else "keypad_one_handed_left"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alignRight) {
            sideRail()
            keyCluster(Modifier.weight(1f))
        } else {
            keyCluster(Modifier.weight(1f))
            sideRail()
        }
    }
}
