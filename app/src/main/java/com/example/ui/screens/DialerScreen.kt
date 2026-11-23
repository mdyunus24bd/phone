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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.preferences.AutoRecordMode
import com.example.data.preferences.DialerSizeOption
import com.example.data.preferences.KeypadLayoutStyle
import com.example.telecom.PhoneCountryResolver
import com.example.telecom.SimSlotInfo
import com.example.ui.components.ContactAvatar
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.awaitCancellation

/**
 * Sanitizes a dialer TextFieldValue (e.g. after native long-press Paste or Cut) so only
 * dialable characters remain while keeping the cursor at the corresponding offset.
 */
private fun sanitizeDialFieldValue(value: TextFieldValue): TextFieldValue {
    val raw = value.text
    val out = StringBuilder()
    var mappedStart = 0
    var mappedEnd = 0
    val rawStart = value.selection.start.coerceIn(0, raw.length)
    val rawEnd = value.selection.end.coerceIn(0, raw.length)

    for (i in raw.indices) {
        if (i == rawStart) mappedStart = out.length
        if (i == rawEnd) mappedEnd = out.length
        val ch = raw[i]
        val keep = when {
            ch in '0'..'9' || ch == '*' || ch == '#' || ch == ',' || ch == ';' -> true
            ch == '+' && out.isEmpty() -> true
            else -> false
        }
        if (keep && out.length < 32) {
            out.append(ch)
        }
    }
    if (rawStart >= raw.length) mappedStart = out.length
    if (rawEnd >= raw.length) mappedEnd = out.length

    val clean = out.toString()
    return TextFieldValue(
        text = clean,
        selection = TextRange(
            mappedStart.coerceIn(0, clean.length),
            mappedEnd.coerceIn(0, clean.length)
        )
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun DialerScreen(
    dialInput: String,
    selectedSimIndex: Int,
    activeSims: List<SimSlotInfo>,
    contacts: List<ContactEntity>,
    callLogs: List<CallLogEntity> = emptyList(),
    isDefaultDialer: Boolean,
    hasPhoneStatePermission: Boolean,
    themeMode: ThemeMode,
    keypadLayout: KeypadLayoutStyle,
    dialerSize: DialerSizeOption = DialerSizeOption.MEDIUM,
    autoRecordEnabled: Boolean,
    autoRecordMode: AutoRecordMode,
    autoRecordSpecificCount: Int,
    onDigitClick: (Char) -> Unit,
    onPlayDialTone: (Char) -> Unit = {},
    onSetDialInput: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSelectSimIndex: (Int) -> Unit,
    onSelectKeypadLayout: (KeypadLayoutStyle) -> Unit,
    onSelectDialerSize: (DialerSizeOption) -> Unit = {},
    onToggleLightDarkTheme: () -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onRequestPhonePermissions: () -> Unit,
    onOpenPhoneSimSettings: () -> Unit,
    onPlaceCall: (String, SimSlotInfo?) -> Unit,
    onAddNewContact: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToRecents: () -> Unit = {},
    onNavigateToContacts: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var showLayoutMenu by remember { mutableStateOf(false) }

    // Track cursor / selection position so tapping any digit lets the user remove or insert at that exact spot
    var lastSyncedText by remember { mutableStateOf(dialInput) }
    var cursorSelection by remember { mutableStateOf(TextRange(dialInput.length)) }
    if (dialInput != lastSyncedText) {
        lastSyncedText = dialInput
        cursorSelection = TextRange(dialInput.length)
    }
    val safeSelection = TextRange(
        start = cursorSelection.start.coerceIn(0, dialInput.length),
        end = cursorSelection.end.coerceIn(0, dialInput.length)
    )
    val dialFieldValue = TextFieldValue(
        text = dialInput,
        selection = safeSelection
    )

    val handleInsertDigitAtCursor: (Char) -> Unit = { ch ->
        val start = minOf(safeSelection.start, safeSelection.end)
        val end = maxOf(safeSelection.start, safeSelection.end)
        if (start == dialInput.length && end == dialInput.length) {
            if (dialInput.length < 32) {
                val updated = dialInput + ch
                lastSyncedText = updated
                cursorSelection = TextRange(updated.length)
                onDigitClick(ch)
            }
        } else {
            val updated = (dialInput.substring(0, start) + ch + dialInput.substring(end)).take(32)
            val nextCursor = (start + 1).coerceAtMost(updated.length)
            lastSyncedText = updated
            cursorSelection = TextRange(nextCursor)
            onSetDialInput(updated)
            onPlayDialTone(ch)
        }
    }

    val handleBackspaceAtCursor: () -> Unit = {
        val start = minOf(safeSelection.start, safeSelection.end)
        val end = maxOf(safeSelection.start, safeSelection.end)
        when {
            dialInput.isEmpty() -> Unit
            start != end -> {
                val updated = dialInput.removeRange(start, end)
                lastSyncedText = updated
                cursorSelection = TextRange(start.coerceIn(0, updated.length))
                onSetDialInput(updated)
            }
            start > 0 -> {
                val updated = dialInput.removeRange(start - 1, start)
                lastSyncedText = updated
                cursorSelection = TextRange((start - 1).coerceIn(0, updated.length))
                if (start == dialInput.length) {
                    onBackspace()
                } else {
                    onSetDialInput(updated)
                }
            }
        }
    }

    val handleClearAll: () -> Unit = {
        lastSyncedText = ""
        cursorSelection = TextRange(0)
        onClear()
    }

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

    val groupedRecentCalls = remember(callLogs) {
        groupCallLogsByUniqueNumber(callLogs, preferLatestMissedAsRepresentative = false)
    }

    val displayedRecentLogs = remember(groupedRecentCalls, dialInput, contactsById, contactsByDigits) {
        val query = dialInput.trim()
        val digits = query.filter { it.isDigit() }
        if (query.isEmpty()) {
            groupedRecentCalls.take(12)
        } else {
            groupedRecentCalls.filter { group ->
                val log = group.latestLog
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
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Dialer Size",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            DialerSizeOption.entries.forEach { sizeOption ->
                                val isSizeSelected = dialerSize == sizeOption
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = sizeOption.label,
                                            fontWeight = if (isSizeSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSizeSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = if (isSizeSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected size",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onClick = {
                                        showLayoutMenu = false
                                        onSelectDialerSize(sizeOption)
                                    },
                                    modifier = Modifier.testTag("keypad_size_option_${sizeOption.name}")
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
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToFavorites,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = "Favorites"
                        )
                    },
                    label = { Text("Favorites") },
                    modifier = Modifier.testTag("keypad_nav_favorites")
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToRecents,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Recents"
                        )
                    },
                    label = { Text("Recents") },
                    modifier = Modifier.testTag("keypad_nav_recents")
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToContacts,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Contacts,
                            contentDescription = "Contacts"
                        )
                    },
                    label = { Text("Contacts") },
                    modifier = Modifier.testTag("keypad_nav_contacts")
                )
                NavigationBarItem(
                    selected = true,
                    onClick = {},
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Dialpad,
                            contentDescription = "Keypad"
                        )
                    },
                    label = { Text("Keypad") },
                    modifier = Modifier.testTag("keypad_nav_keypad")
                )
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            val compactHeight = maxHeight < 520.dp || (dialerSize.scale > 1.08f && maxHeight < 640.dp)

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
                            items(displayedRecentLogs, key = { "kp_recent_${it.numberKey}" }) { group ->
                                val log = group.latestLog
                                val logDigits = log.phoneNumber.filter { it.isDigit() }
                                val matched = log.contactInfo.contactId?.let { contactsById[it] }
                                    ?: contactsByDigits[logDigits]
                                val country = remember(log.phoneNumber) {
                                    PhoneCountryResolver.resolveCountryName(context, log.phoneNumber)
                                }
                                KeypadRecentCallRow(
                                    group = group,
                                    matchedContact = matched,
                                    callingCountry = country,
                                    onFillNumber = { onSetDialInput(log.phoneNumber) },
                                    onCallNow = { onPlaceCall(log.phoneNumber, null) }
                                )
                            }
                        }
                    }
                }

                // Borderless Dial Number Display with Cursor-Targeted Specific Digit Removal
                val displayScale = dialerSize.scale.coerceIn(0.84f, 1.24f)
                val displayFontSize = if (dialInput.length > 13) {
                    (24f * displayScale).sp
                } else {
                    (32f * displayScale).sp
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = (6f * displayScale).dp.coerceIn(4.dp, 10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Balanced spacer so entered digits stay visually centered
                        Spacer(Modifier.width(48.dp))

                        // Tap anywhere in the number to move the cursor or drag to select specific digits
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InterceptPlatformTextInput(
                                interceptor = { _, _ -> awaitCancellation() }
                            ) {
                                BasicTextField(
                                    value = dialFieldValue,
                                    onValueChange = { updatedValue ->
                                        if (updatedValue.text == dialInput) {
                                            cursorSelection = updatedValue.selection
                                        } else {
                                            val sanitized = sanitizeDialFieldValue(updatedValue)
                                            lastSyncedText = sanitized.text
                                            cursorSelection = sanitized.selection
                                            onSetDialInput(sanitized.text)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = displayFontSize,
                                        lineHeight = (displayFontSize.value * 1.18f).sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("dial_display_text")
                                )
                            }
                        }

                        // Backspace: tap removes specific digit/selection at cursor; long-press clears all
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    enabled = dialInput.isNotEmpty(),
                                    onClick = handleBackspaceAtCursor,
                                    onLongClick = {
                                        if (dialInput.isNotEmpty()) {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            handleClearAll()
                                        }
                                    }
                                )
                                .testTag("dial_backspace_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dialInput.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                                    contentDescription = "Delete digit at cursor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                }

                // Dynamic Switchable Keypad Layouts scaled by DialerSizeOption
                AnimatedContent(
                    targetState = keypadLayout,
                    label = "keypad_layout_transition"
                ) { layout ->
                    when (layout) {
                        KeypadLayoutStyle.CIRCULAR_MODERN -> CircularModernKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.COMPACT_TACTILE -> CompactTactileKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.MINIMAL_CLEAN -> MinimalCleanKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.ACCENT_RING -> AccentRingKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.SOFT_CAPSULE -> SoftCapsuleKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.CLASSIC_BOARD -> ClassicBoardKeypad(
                            sizeScale = dialerSize.scale,
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            }
                        )

                        KeypadLayoutStyle.ONE_HANDED_RIGHT -> OneHandedKeypad(
                            alignRight = true,
                            sizeScale = dialerSize.scale,
                            dialInputNotEmpty = dialInput.isNotEmpty(),
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            },
                            onSwapHand = { onSelectKeypadLayout(KeypadLayoutStyle.ONE_HANDED_LEFT) },
                            onBackspace = handleBackspaceAtCursor,
                            onClear = handleClearAll
                        )

                        KeypadLayoutStyle.ONE_HANDED_LEFT -> OneHandedKeypad(
                            alignRight = false,
                            sizeScale = dialerSize.scale,
                            dialInputNotEmpty = dialInput.isNotEmpty(),
                            onKeyPress = { ch ->
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                handleInsertDigitAtCursor(ch)
                            },
                            onZeroLongPress = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                handleInsertDigitAtCursor('+')
                            },
                            onSwapHand = { onSelectKeypadLayout(KeypadLayoutStyle.ONE_HANDED_RIGHT) },
                            onBackspace = handleBackspaceAtCursor,
                            onClear = handleClearAll
                        )
                    }
                }

                // Bottom Action Row:
                // - If a default calling SIM is selected in Android Phone Settings (or <= 1 SIM active):
                //   show ONE clean Call icon button.
                // - If 2+ SIMs are active and NO default calling SIM is selected ("Ask every time"):
                //   show TWO Call icon buttons with each SIM's detected company name inside.
                val showDualSimCallButtons = activeSims.size >= 2 && activeSims.none { it.isSystemDefault }
                val dualSimPair = if (showDualSimCallButtons) activeSims.take(2) else emptyList()

                val sideActionSize = if (showDualSimCallButtons) {
                    (48f * dialerSize.scale).dp.coerceIn(48.dp, 56.dp)
                } else {
                    (54f * dialerSize.scale).dp.coerceIn(48.dp, 64.dp)
                }
                val callActionSize = (68f * dialerSize.scale).dp.coerceIn(58.dp, 82.dp)
                val callIconSize = (30f * dialerSize.scale).dp.coerceIn(24.dp, 36.dp)
                val dualCallButtonHeight = (62f * dialerSize.scale).dp.coerceIn(56.dp, 74.dp)
                val dualCallIconSize = (22f * dialerSize.scale).dp.coerceIn(19.dp, 26.dp)
                val dualCompanyFontSize = (11f * dialerSize.scale).coerceIn(10f, 13f).sp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (showDualSimCallButtons) {
                        Arrangement.spacedBy(8.dp)
                    } else {
                        Arrangement.SpaceEvenly
                    }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(sideActionSize)
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
                                modifier = Modifier.size((22f * dialerSize.scale).dp.coerceIn(19.dp, 26.dp))
                            )
                        }
                    }

                    if (showDualSimCallButtons) {
                        dualSimPair.forEachIndexed { idx, sim ->
                            val companyName = sim.carrierName.ifBlank { sim.displayName }.trim()
                            val buttonBrush = if (idx == 0) {
                                Brush.horizontalGradient(listOf(CallAcceptGreen, SimAccentEmerald))
                            } else {
                                Brush.horizontalGradient(listOf(EmeraldPrimaryLight, CallAcceptGreen))
                            }
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(dualCallButtonHeight)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(buttonBrush)
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onPlaceCall(dialInput, sim)
                                    }
                                    .testTag(if (idx == 0) "place_call_button_sim_0" else "place_call_button_sim_1")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Call,
                                        contentDescription = if (companyName.isNotBlank()) {
                                            "Call with $companyName"
                                        } else {
                                            "Call"
                                        },
                                        tint = Color.White,
                                        modifier = Modifier.size(
                                            if (companyName.isNotBlank()) dualCallIconSize else callIconSize
                                        )
                                    )
                                    if (companyName.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = companyName,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = dualCompanyFontSize,
                                                lineHeight = (dualCompanyFontSize.value * 1.15f).sp
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Clean Primary Call Icon-Only Action Button (default calling SIM selected)
                        Surface(
                            shape = CircleShape,
                            color = Color.Transparent,
                            modifier = Modifier
                                .size(callActionSize)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(listOf(CallAcceptGreen, SimAccentEmerald))
                                )
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onPlaceCall(dialInput, null)
                                }
                                .testTag("place_call_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(callIconSize)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .size(sideActionSize)
                            .clip(CircleShape)
                            .clickable(enabled = dialInput.isNotEmpty()) { handleClearAll() }
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
    group: GroupedRecentCall,
    matchedContact: ContactEntity?,
    callingCountry: String,
    onFillNumber: () -> Unit,
    onCallNow: () -> Unit
) {
    val log = group.latestLog
    val isMissed = log.direction == CallDirection.MISSED
    val showMissedCount = isMissed && group.missedCalls > 0
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val baseTitle = resolvedName.ifBlank { log.phoneNumber }
                    Text(
                        text = baseTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isMissed) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isMissed) CallEndRed else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showMissedCount) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = CallEndRed.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "${group.missedCalls} missed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = CallEndRed,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SimSlotBadge(
                        routeLabel = log.routeLabel,
                        compact = true,
                        modifier = Modifier.testTag("keypad_recent_sim_${log.id}")
                    )
                    Text(
                        text = buildString {
                            if (showMissedCount) append("Missed (${group.missedCalls}) • ")
                            if (resolvedName.isNotBlank()) append("${log.phoneNumber} • ")
                            if (callingCountry.isNotBlank()) append("$callingCountry • ")
                            append(formatTimestamp(log.timestamp))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMissed) CallEndRed.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
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
