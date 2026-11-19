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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncLock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import com.example.ui.components.NetworkBadge
import com.example.ui.components.NumberCallHistoryDialog
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimestamp
import com.example.ui.components.matchesCallHistoryTarget
import com.example.ui.components.normalizeCallHistoryKey
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SpamWarningCoral
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class RecentsFilter(val label: String) {
    ALL("All"),
    MISSED("Missed"),
    RECORDED("Recorded"),
    BLOCKED("Spam")
}

@Composable
fun RecentsScreen(
    callLogs: List<CallLogEntity>,
    contacts: List<ContactEntity>,
    isDefaultDialer: Boolean,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    hasCallLogPermission: Boolean,
    isSyncingCallLogs: Boolean,
    lastAutoSyncTimestamp: Long,
    lastSyncTimestamp: Long,
    lastSyncChecksum: String,
    onCallBack: (String) -> Unit,
    onOpenAddContact: (phoneNumber: String, prefillName: String) -> Unit,
    onRequestDefaultDialer: () -> Unit,
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onDeleteRecording: (CallRecordingEntity) -> Unit,
    onReportSpam: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onSyncSystemCallLog: () -> Unit,
    onOpenEncryptedSync: () -> Unit,
    onOpenRecordingSettings: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(RecentsFilter.ALL) }
    var historyTargetLog by remember { mutableStateOf<CallLogEntity?>(null) }
    var expandedLogId by remember { mutableStateOf<Long?>(null) }
    var openSwipeLogId by remember { mutableStateOf<Long?>(null) }
    val recordingsById = remember(recordings) { recordings.associateBy { it.id } }
    val contactsById = remember(contacts) { contacts.associateBy { it.id } }
    val contactsByDigits = remember(contacts) {
        contacts.mapNotNull { c ->
            val d = c.phoneNumber.filter { it.isDigit() }
            if (d.isNotEmpty()) d to c else null
        }.toMap()
    }
    val callsCountByNumberKey = remember(callLogs) {
        callLogs.groupingBy { normalizeCallHistoryKey(it.phoneNumber) }.eachCount()
    }

    val filteredLogs = remember(callLogs, selectedFilter) {
        when (selectedFilter) {
            RecentsFilter.ALL -> callLogs
            RecentsFilter.MISSED -> callLogs.filter { it.direction == CallDirection.MISSED }
            RecentsFilter.RECORDED -> callLogs.filter { it.recordingId != null }
            RecentsFilter.BLOCKED -> callLogs.filter {
                it.direction == CallDirection.BLOCKED || it.spamScore >= 50
            }
        }
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
                                lastSyncTimestamp > 0L ->
                                    "Vault Synced ${formatTimestamp(lastSyncTimestamp)} • #$lastSyncChecksum"
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

            // Quick Sync Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = onSyncSystemCallLog,
                        enabled = !isSyncingCallLogs,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sync_device_logs_button")
                    ) {
                        if (isSyncingCallLogs) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = "Sync device call log",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = when {
                                isSyncingCallLogs -> "Syncing..."
                                hasCallLogPermission -> "Sync Now"
                                else -> "Enable Auto-Sync"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenEncryptedSync,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_cross_device_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SyncLock,
                            contentDescription = "Encrypted cross-device sync",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Vault", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RecentsFilter.entries) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }
            }

            // App Internal Storage Call Recordings Strip
            if (recordings.isNotEmpty() && (selectedFilter == RecentsFilter.ALL || selectedFilter == RecentsFilter.RECORDED)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Internal Storage Recordings (${recordings.size})",
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
                items(recordings, key = { "rec_${it.id}" }) { rec ->
                    val isPlaying = playingRecordingId == rec.id
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconButton(
                                onClick = { onTogglePlayback(rec) },
                                modifier = Modifier.testTag("play_recording_${rec.id}")
                            ) {
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
                                            color = CallEndRed.copy(alpha = 0.18f)
                                        ) {
                                            Text(
                                                text = "AUTO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CallEndRed,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = if (rec.encryptedAtRest) Icons.Filled.Lock else Icons.Filled.Folder,
                                        contentDescription = if (rec.encryptedAtRest) "Encrypted recording" else "Saved in App Internal Storage",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                if (rec.storagePath.isNotBlank()) {
                                    Text(
                                        text = rec.storagePath,
                                        style = MonoMetricStyle,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "${formatDuration(rec.durationSec)} • ${rec.fileSizeBytes / 1024} KB • Internal Storage • ${formatTimestamp(rec.createdAt)}",
                                    style = MonoMetricStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onDeleteRecording(rec) }) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Delete recording",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Call History Empty State or List
            if (filteredLogs.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No Call History Yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Place a SIM call from the Keypad, import your device call log, or restore an AES-256 encrypted sync bundle.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    val linkedRec = log.recordingId?.let { recordingsById[it] }
                    val logDigits = log.phoneNumber.filter { it.isDigit() }
                    val matchedContact = log.contactInfo.contactId?.let { contactsById[it] }
                        ?: contactsByDigits[logDigits]
                    val totalForNumber = callsCountByNumberKey[normalizeCallHistoryKey(log.phoneNumber)] ?: 1
                    SwipeableCallLogItem(
                        log = log,
                        matchedContact = matchedContact,
                        totalCallsForNumber = totalForNumber,
                        hasRecording = linkedRec != null,
                        isPlayingRecording = linkedRec != null && playingRecordingId == linkedRec.id,
                        isExpanded = expandedLogId == log.id,
                        isSwipedOpen = openSwipeLogId == log.id,
                        onToggleExpand = {
                            openSwipeLogId = null
                            expandedLogId = if (expandedLogId == log.id) null else log.id
                        },
                        onSwipeOpenChange = { shouldOpen ->
                            openSwipeLogId = if (shouldOpen) log.id else if (openSwipeLogId == log.id) null else openSwipeLogId
                        },
                        onSwipeStart = {
                            if (openSwipeLogId != null && openSwipeLogId != log.id) {
                                openSwipeLogId = null
                            }
                        },
                        onOpenHistory = { historyTargetLog = log },
                        onCallBack = { onCallBack(log.phoneNumber) },
                        onAddNewContact = { onOpenAddContact(log.phoneNumber, log.resolvedContactName) },
                        onPlayRecording = { linkedRec?.let(onTogglePlayback) },
                        onReportSpam = { onReportSpam(log.phoneNumber) },
                        onDelete = {
                            if (openSwipeLogId == log.id) openSwipeLogId = null
                            if (expandedLogId == log.id) expandedLogId = null
                            onDeleteLog(log.id)
                        }
                    )
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
            onReportSpam = onReportSpam,
            onDeleteLog = onDeleteLog
        )
    }
}

@Composable
private fun SwipeableCallLogItem(
    log: CallLogEntity,
    matchedContact: ContactEntity?,
    totalCallsForNumber: Int,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    isExpanded: Boolean,
    isSwipedOpen: Boolean,
    onToggleExpand: () -> Unit,
    onSwipeOpenChange: (Boolean) -> Unit,
    onSwipeStart: () -> Unit,
    onOpenHistory: () -> Unit,
    onCallBack: () -> Unit,
    onAddNewContact: () -> Unit,
    onPlayRecording: () -> Unit,
    onReportSpam: () -> Unit,
    onDelete: () -> Unit
) {
    val density = LocalDensity.current
    val revealWidthDp = 96.dp
    val revealWidthPx = with(density) { revealWidthDp.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember(log.id) { Animatable(if (isSwipedOpen) -revealWidthPx else 0f) }

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
                .pointerInput(log.id, revealWidthPx) {
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
                log = log,
                matchedContact = matchedContact,
                totalCallsForNumber = totalCallsForNumber,
                hasRecording = hasRecording,
                isPlayingRecording = isPlayingRecording,
                isExpanded = isExpanded,
                onCardClick = {
                    if (isSwipedOpen) {
                        onSwipeOpenChange(false)
                    } else {
                        onToggleExpand()
                    }
                },
                onOpenHistory = onOpenHistory,
                onCallBack = onCallBack,
                onAddNewContact = onAddNewContact,
                onPlayRecording = onPlayRecording,
                onReportSpam = onReportSpam,
                onDelete = onDelete
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CallLogCard(
    log: CallLogEntity,
    matchedContact: ContactEntity?,
    totalCallsForNumber: Int,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    isExpanded: Boolean,
    onCardClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onCallBack: () -> Unit,
    onAddNewContact: () -> Unit,
    onPlayRecording: () -> Unit,
    onReportSpam: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
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
        border = if (isMissed) BorderStroke(1.dp, CallEndRed.copy(alpha = 0.30f)) else null,
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
            // Clean default Recents row (Avatar, Name/Number, Timestamp/Duration, Country, Call Back)
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
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = resolvedName.ifBlank { log.phoneNumber },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isMissed) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isMissed) CallEndRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (contactLabel.isNotBlank()) {
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
                    Text(
                        text = buildString {
                            if (isMissed) append("Missed • ")
                            if (resolvedName.isNotBlank()) append("${log.phoneNumber} • ")
                            append(formatTimestamp(log.timestamp))
                            if (log.durationSec > 0L) append(" • ${formatDuration(log.durationSec)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMissed) CallEndRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (callingCountry.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Calling country",
                                tint = if (isMissed) CallEndRed.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = callingCountry,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isMissed) CallEndRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
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

            // Expanded inside-number details & options (SIM, MOS, Calls count, Add, History, Spam Shield, Delete)
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

                    // Telemetry & Call Count Badges inside clicked number
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NetworkBadge(networkType = log.networkType)
                        if (log.mosScore > 0f) {
                            MosBadge(mosScore = log.mosScore)
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "$totalCallsForNumber ${if (totalCallsForNumber == 1) "call" else "calls"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
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
                        if (log.spamScore >= 50 || log.direction == CallDirection.BLOCKED) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SpamWarningCoral.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "SPAM ${log.spamScore}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpamWarningCoral,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // Action Buttons Row (Add, History, Play Recording if present, Spam Shield, Delete)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
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
                        if (hasRecording) {
                            ExpandedNumberActionTile(
                                icon = if (isPlayingRecording) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                                label = if (isPlayingRecording) "Stop" else "Audio",
                                tint = MaterialTheme.colorScheme.primary,
                                onClick = onPlayRecording,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("play_recent_recording_${log.id}")
                            )
                        }
                        ExpandedNumberActionTile(
                            icon = Icons.Filled.Shield,
                            label = "Spam Shield",
                            tint = SpamWarningCoral,
                            onClick = onReportSpam,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("spam_shield_recent_${log.id}")
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
