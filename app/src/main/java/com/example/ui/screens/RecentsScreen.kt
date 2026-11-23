package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.telecom.PhoneCountryResolver
import com.example.ui.components.ContactAvatar
import com.example.ui.components.MosBadge
import com.example.ui.components.NumberCallHistoryDialog
import com.example.ui.components.QuickCallNoteDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimestamp
import com.example.ui.components.matchesCallHistoryTarget
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class RecentsFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    NOTES("Notes"),
    RECORDED("Recorded")
}

@Composable
fun RecentsScreen(
    callLogs: List<CallLogEntity>,
    contacts: List<ContactEntity>,
    isDefaultDialer: Boolean,
    canShowIncomingOverDisplay: Boolean = true,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    hasCallLogPermission: Boolean,
    isSyncingCallLogs: Boolean,
    lastAutoSyncTimestamp: Long,
    onCallBack: (String) -> Unit,
    onOpenAddContact: (phoneNumber: String, prefillName: String) -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onEnableIncomingOverDisplay: () -> Unit = {},
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onShareRecording: (CallRecordingEntity) -> Unit = {},
    onDeleteRecording: (CallRecordingEntity) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onSaveCallNote: (Long, String) -> Unit = { _, _ -> },
    onOpenRecordingSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(RecentsFilter.ALL) }
    var historyTargetLog by remember { mutableStateOf<CallLogEntity?>(null) }
    var noteTargetLog by remember { mutableStateOf<CallLogEntity?>(null) }
    var expandedNumberKey by remember { mutableStateOf<String?>(null) }
    var openSwipeNumberKey by remember { mutableStateOf<String?>(null) }
    val recordingsById = remember(recordings) { recordings.associateBy { it.id } }
    val contactsById = remember(contacts) { contacts.associateBy { it.id } }
    val contactsByDigits = remember(contacts) {
        contacts.mapNotNull { c ->
            val d = c.phoneNumber.filter { it.isDigit() }
            if (d.isNotEmpty()) d to c else null
        }.toMap()
    }

    // Deduplicated groups: each phone number appears ONLY ONCE
    val allRecentGroups = remember(callLogs) {
        groupCallLogsByUniqueNumber(callLogs, preferLatestMissedAsRepresentative = false)
    }
    val missedRecentGroups = remember(callLogs) {
        groupCallLogsByUniqueNumber(callLogs, preferLatestMissedAsRepresentative = true)
            .filter { it.missedCalls > 0 }
    }
    val notedLogs = remember(callLogs) {
        callLogs
            .filter { it.quickNote.isNotBlank() }
            .sortedByDescending { it.timestamp }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recents",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = when {
                                isSyncingCallLogs -> "Auto-syncing device call history..."
                                hasCallLogPermission && lastAutoSyncTimestamp > 0L ->
                                    "Auto-Sync Active • Updated ${formatTimestamp(lastAutoSyncTimestamp)}"
                                hasCallLogPermission -> "Auto-Sync Active • Tap number for options • Slide left to delete"
                                else -> "Tap number for options • Slide left to delete"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (hasCallLogPermission) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = CallAcceptGreen.copy(alpha = 0.16f),
                                modifier = Modifier.testTag("recents_auto_sync_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (isSyncingCallLogs) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = CallAcceptGreen
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Auto-Sync active",
                                            tint = CallAcceptGreen,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isSyncingCallLogs) "Syncing" else "Auto",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CallAcceptGreen
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("recents_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!isDefaultDialer) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhoneAndroid,
                                    contentDescription = "Default Phone",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Set VoxLine as Default Phone",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "Enable full SIM call HUD & recording",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = onRequestDefaultDialer,
                                modifier = Modifier.testTag("recents_set_default_dialer_button")
                            ) {
                                Text("Enable")
                            }
                        }
                    }
                }
            }

            if (!canShowIncomingOverDisplay) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Incoming call over display",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Show Incoming Calls Over Display",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        text = "Wake screen & pop incoming calls over lock screen and apps",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(
                                onClick = onEnableIncomingOverDisplay,
                                modifier = Modifier.testTag("recents_enable_over_display_button")
                            ) {
                                Text("Allow")
                            }
                        }
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecentsFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = {
                                selectedFilter = filter
                                expandedNumberKey = null
                                openSwipeNumberKey = null
                            },
                            label = {
                                Text(filter.label)
                            },
                            modifier = Modifier.testTag("recents_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            // Strictly isolated section per selected menu
            when (selectedFilter) {
                RecentsFilter.ALL -> {
                    if (allRecentGroups.isEmpty()) {
                        item {
                            RecentsEmptyStateCard(
                                icon = Icons.Filled.History,
                                title = "No Recent Calls Yet",
                                subtitle = "Place a SIM call from the Keypad or receive a call to see it listed here."
                            )
                        }
                    } else {
                        items(allRecentGroups, key = { "all_${it.numberKey}" }) { group ->
                            val log = group.latestLog
                            val logDigits = log.phoneNumber.filter { it.isDigit() }
                            val matchedContact = log.contactInfo.contactId?.let { contactsById[it] }
                                ?: contactsByDigits[logDigits]
                            SwipeableCallLogItem(
                                group = group,
                                matchedContact = matchedContact,
                                isExpanded = expandedNumberKey == group.numberKey,
                                isSwipedOpen = openSwipeNumberKey == group.numberKey,
                                onToggleExpand = {
                                    openSwipeNumberKey = null
                                    expandedNumberKey = if (expandedNumberKey == group.numberKey) null else group.numberKey
                                },
                                onSwipeOpenChange = { shouldOpen ->
                                    openSwipeNumberKey = if (shouldOpen) {
                                        group.numberKey
                                    } else if (openSwipeNumberKey == group.numberKey) {
                                        null
                                    } else {
                                        openSwipeNumberKey
                                    }
                                },
                                onSwipeStart = {
                                    if (openSwipeNumberKey != null && openSwipeNumberKey != group.numberKey) {
                                        openSwipeNumberKey = null
                                    }
                                },
                                onOpenHistory = { historyTargetLog = log },
                                onOpenNote = { noteTargetLog = log },
                                onCallBack = { onCallBack(log.phoneNumber) },
                                onAddNewContact = { onOpenAddContact(log.phoneNumber, log.resolvedContactName) },
                                onDelete = {
                                    if (openSwipeNumberKey == group.numberKey) openSwipeNumberKey = null
                                    if (expandedNumberKey == group.numberKey) expandedNumberKey = null
                                    group.logs.forEach { onDeleteLog(it.id) }
                                }
                            )
                        }
                    }
                }

                RecentsFilter.MISSED -> {
                    if (missedRecentGroups.isEmpty()) {
                        item {
                            RecentsEmptyStateCard(
                                icon = Icons.AutoMirrored.Filled.CallMissed,
                                title = "No Missed Calls",
                                subtitle = "You have no missed calls in your recent call history."
                            )
                        }
                    } else {
                        items(missedRecentGroups, key = { "missed_${it.numberKey}" }) { group ->
                            val log = group.latestLog
                            val logDigits = log.phoneNumber.filter { it.isDigit() }
                            val matchedContact = log.contactInfo.contactId?.let { contactsById[it] }
                                ?: contactsByDigits[logDigits]
                            SwipeableCallLogItem(
                                group = group,
                                matchedContact = matchedContact,
                                isExpanded = expandedNumberKey == group.numberKey,
                                isSwipedOpen = openSwipeNumberKey == group.numberKey,
                                onToggleExpand = {
                                    openSwipeNumberKey = null
                                    expandedNumberKey = if (expandedNumberKey == group.numberKey) null else group.numberKey
                                },
                                onSwipeOpenChange = { shouldOpen ->
                                    openSwipeNumberKey = if (shouldOpen) {
                                        group.numberKey
                                    } else if (openSwipeNumberKey == group.numberKey) {
                                        null
                                    } else {
                                        openSwipeNumberKey
                                    }
                                },
                                onSwipeStart = {
                                    if (openSwipeNumberKey != null && openSwipeNumberKey != group.numberKey) {
                                        openSwipeNumberKey = null
                                    }
                                },
                                onOpenHistory = { historyTargetLog = log },
                                onOpenNote = { noteTargetLog = log },
                                onCallBack = { onCallBack(log.phoneNumber) },
                                onAddNewContact = { onOpenAddContact(log.phoneNumber, log.resolvedContactName) },
                                onDelete = {
                                    if (openSwipeNumberKey == group.numberKey) openSwipeNumberKey = null
                                    if (expandedNumberKey == group.numberKey) expandedNumberKey = null
                                    group.logs.forEach { onDeleteLog(it.id) }
                                }
                            )
                        }
                    }
                }

                RecentsFilter.NOTES -> {
                    if (notedLogs.isEmpty()) {
                        item {
                            RecentsEmptyStateCard(
                                icon = Icons.Filled.EditNote,
                                title = "No Call Notes Yet",
                                subtitle = "Tap any call in Recent Calls and choose Note to save a quick text note for that call."
                            )
                        }
                    } else {
                        item {
                            Text(
                                text = "Call Notes (${notedLogs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(notedLogs, key = { "note_${it.id}" }) { log ->
                            val logDigits = log.phoneNumber.filter { it.isDigit() }
                            val matchedContact = log.contactInfo.contactId?.let { contactsById[it] }
                                ?: contactsByDigits[logDigits]
                            CallNoteMenuCard(
                                log = log,
                                matchedContact = matchedContact,
                                onEditNote = { noteTargetLog = log },
                                onCallBack = { onCallBack(log.phoneNumber) }
                            )
                        }
                    }
                }

                RecentsFilter.RECORDED -> {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recorded Calls (${recordings.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = onOpenRecordingSettings,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("recents_auto_record_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FiberManualRecord,
                                    contentDescription = null,
                                    tint = CallEndRed,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(5.dp))
                                Text("Auto-Rec Rules", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    if (recordings.isEmpty()) {
                        item {
                            RecentsEmptyStateCard(
                                icon = Icons.Filled.GraphicEq,
                                title = "No Call Recordings Yet",
                                subtitle = "Record a call from the active call screen or enable Automatic Call Recording in Recording Settings.",
                                actionLabel = "Recording Settings",
                                onAction = onOpenRecordingSettings
                            )
                        }
                    } else {
                        items(recordings, key = { "rec_${it.id}" }) { rec ->
                            RecordedCallCard(
                                recording = rec,
                                isPlaying = playingRecordingId == rec.id,
                                onTogglePlayback = { onTogglePlayback(rec) },
                                onShare = { onShareRecording(rec) },
                                onCallBack = { onCallBack(rec.phoneNumber) },
                                onDelete = { onDeleteRecording(rec) }
                            )
                        }
                    }
                }
            }
        }
    }

    historyTargetLog?.let { targetLog ->
        val logDigits = targetLog.phoneNumber.filter { it.isDigit() }
        val matched = targetLog.contactInfo.contactId?.let { contactsById[it] }
            ?: contactsByDigits[logDigits]
        val allForNumber = remember(callLogs, targetLog, matched) {
            callLogs.filter { matchesCallHistoryTarget(it, targetLog.phoneNumber, matched) }
        }
        NumberCallHistoryDialog(
            phoneNumber = targetLog.phoneNumber,
            matchedContact = matched,
            allLogsForNumber = allForNumber,
            recordingsById = recordingsById,
            playingRecordingId = playingRecordingId,
            onDismiss = { historyTargetLog = null },
            onCallSim = { num ->
                historyTargetLog = null
                onCallBack(num)
            },
            onAddContact = if (matched == null) {
                {
                    historyTargetLog = null
                    onOpenAddContact(targetLog.phoneNumber, targetLog.resolvedContactName)
                }
            } else {
                null
            },
            onTogglePlayback = onTogglePlayback,
            onShareRecording = onShareRecording,
            onDeleteLog = onDeleteLog,
            onEditNote = { entry -> noteTargetLog = entry }
        )
    }

    noteTargetLog?.let { targetLog ->
        val liveLog = callLogs.firstOrNull { it.id == targetLog.id } ?: targetLog
        val logDigits = liveLog.phoneNumber.filter { it.isDigit() }
        val matched = liveLog.contactInfo.contactId?.let { contactsById[it] }
            ?: contactsByDigits[logDigits]
        val callerLabel = matched?.displayName?.ifBlank { liveLog.resolvedContactName }
            ?: liveLog.resolvedContactName.ifBlank { liveLog.phoneNumber }
        QuickCallNoteDialog(
            log = liveLog,
            callerLabel = callerLabel,
            onSave = { updatedNote ->
                onSaveCallNote(liveLog.id, updatedNote)
                noteTargetLog = null
            },
            onDismiss = { noteTargetLog = null }
        )
    }
}

@Composable
private fun SwipeableCallLogItem(
    group: GroupedRecentCall,
    matchedContact: ContactEntity?,
    isExpanded: Boolean,
    isSwipedOpen: Boolean,
    onToggleExpand: () -> Unit,
    onSwipeOpenChange: (Boolean) -> Unit,
    onSwipeStart: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNote: () -> Unit,
    onCallBack: () -> Unit,
    onAddNewContact: () -> Unit,
    onDelete: () -> Unit
) {
    val log = group.latestLog
    val density = LocalDensity.current
    val revealWidthDp = 96.dp
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(group.numberKey) { Animatable(if (isSwipedOpen) -revealWidthPx else 0f) }

    LaunchedEffect(isSwipedOpen, revealWidthPx) {
        val target = if (isSwipedOpen) -revealWidthPx else 0f
        if (offsetX.targetValue != target) {
            offsetX.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
    ) {
        // Revealed Delete action behind the right edge when sliding left
        if (offsetX.value < -1f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(CallEndRed),
                contentAlignment = Alignment.CenterEnd
            ) {
                Column(
                    modifier = Modifier
                        .width(revealWidthDp)
                        .fillMaxHeight()
                        .clickable(onClick = onDelete)
                        .testTag("swipe_delete_log_${log.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete call log",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Foreground Call Log Card supporting horizontal slide-left reveal and tap-to-expand
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(group.numberKey, revealWidthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            onSwipeStart()
                        },
                        onDragEnd = {
                            val shouldOpen = offsetX.value < -revealWidthPx * 0.4f
                            onSwipeOpenChange(shouldOpen)
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = if (shouldOpen) -revealWidthPx else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onDragCancel = {
                            val shouldOpen = offsetX.value < -revealWidthPx * 0.4f
                            onSwipeOpenChange(shouldOpen)
                            coroutineScope.launch {
                                offsetX.animateTo(
                                    targetValue = if (shouldOpen) -revealWidthPx else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealWidthPx, 0f))
                            }
                        }
                    )
                }
        ) {
            CallLogCard(
                group = group,
                matchedContact = matchedContact,
                isExpanded = isExpanded,
                onCardClick = {
                    if (isSwipedOpen) {
                        onSwipeOpenChange(false)
                    } else {
                        onToggleExpand()
                    }
                },
                onOpenHistory = onOpenHistory,
                onOpenNote = onOpenNote,
                onCallBack = onCallBack,
                onAddNewContact = onAddNewContact,
                onDelete = onDelete
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallLogCard(
    group: GroupedRecentCall,
    matchedContact: ContactEntity?,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenNote: () -> Unit,
    onCallBack: () -> Unit,
    onAddNewContact: () -> Unit,
    onDelete: () -> Unit
) {
    val log = group.latestLog
    val context = LocalContext.current
    val isLatestMissed = log.direction == CallDirection.MISSED
    val showMissedCount = isLatestMissed && group.missedCalls > 0
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
    val contactLabel = matchedContact?.label?.ifBlank { log.contactInfo.label } ?: log.contactInfo.label
    val resolvedName = matchedContact?.displayName?.ifBlank { log.resolvedContactName } ?: log.resolvedContactName
    val isUnknownContact = matchedContact == null
    val callingCountry = remember(log.phoneNumber) {
        PhoneCountryResolver.resolveCountryName(context, log.phoneNumber)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        border = when {
            isLatestMissed -> BorderStroke(1.dp, CallEndRed.copy(alpha = 0.30f))
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
            .testTag("recent_call_card_${log.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Clean unique-number Recents row (Avatar, Name/Number + Missed/Total counts, Timestamp, Country, Call Back)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(46.dp)) {
                    if (matchedContact != null) {
                        ContactAvatar(
                            name = matchedContact.displayName,
                            colorIndex = matchedContact.avatarColorIndex,
                            photoUri = matchedContact.photoUri,
                            size = 44.dp
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = dirIcon,
                                    contentDescription = log.direction.name,
                                    tint = dirColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = dirColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = dirIcon,
                                    contentDescription = log.direction.name,
                                    tint = dirColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val baseTitle = resolvedName.ifBlank { log.phoneNumber }
                        Text(
                            text = baseTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isLatestMissed) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isLatestMissed) CallEndRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (showMissedCount) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = CallEndRed.copy(alpha = 0.16f),
                                modifier = Modifier.testTag("missed_count_badge_${log.id}")
                            ) {
                                Text(
                                    text = "${group.missedCalls} missed",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CallEndRed,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (contactLabel.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = contactLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            modifier = Modifier.testTag("recent_call_sim_${log.id}")
                        )
                        Text(
                            text = buildString {
                                if (showMissedCount) {
                                    append("Missed (${group.missedCalls}) • ")
                                }
                                if (resolvedName.isNotBlank()) append("${log.phoneNumber} • ")
                                append(formatTimestamp(log.timestamp))
                                if (log.durationSec > 0L) append(" • ${formatDuration(log.durationSec)}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isLatestMissed) CallEndRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (callingCountry.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Calling country",
                                tint = if (isLatestMissed) CallEndRed.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = callingCountry,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isLatestMissed) CallEndRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("recent_call_country_${log.id}")
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onCallBack,
                    modifier = Modifier.testTag("redial_log_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Expanded inside-number details & options
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("expanded_number_options_${log.id}"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SimSlotBadge(routeLabel = log.routeLabel, compact = false)
                        if (log.mosScore > 0f) {
                            MosBadge(mosScore = log.mosScore)
                        }
                        if (showMissedCount) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CallEndRed.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${group.missedCalls} missed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CallEndRed,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (log.encryptedTransport) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = "Encrypted Call",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Encrypted",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons Row (Note, Add, History, Delete)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExpandedNumberActionTile(
                            icon = if (log.quickNote.isNotBlank()) Icons.Filled.EditNote else Icons.AutoMirrored.Filled.NoteAdd,
                            label = "Note",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenNote,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("note_recent_log_${log.id}")
                        )
                        if (isUnknownContact && log.phoneNumber.isNotBlank()) {
                            ExpandedNumberActionTile(
                                icon = Icons.Filled.PersonAdd,
                                label = "Add",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onAddNewContact,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_contact_from_recent_${log.id}")
                            )
                        }
                        ExpandedNumberActionTile(
                            icon = Icons.Filled.History,
                            label = "History",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_number_history_${log.id}")
                        )
                        ExpandedNumberActionTile(
                            icon = Icons.Filled.DeleteOutline,
                            label = "Delete",
                            tint = CallEndRed,
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("delete_recent_log_${log.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedNumberActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f),
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
