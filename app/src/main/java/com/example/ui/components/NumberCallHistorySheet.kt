package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.telecom.PhoneCountryResolver
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald
import com.example.ui.theme.SpamWarningCoral
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Normalizes a phone number into a stable grouping key so all call history
 * records for the same phone number are shown together inside the number detail view.
 */
fun normalizeCallHistoryKey(address: String): String {
    val trimmed = address.trim()
    val digits = trimmed.filter { it.isDigit() }
    return if (digits.length >= 10) digits.takeLast(10) else digits.ifEmpty { trimmed.lowercase() }
}

fun matchesCallHistoryTarget(
    log: CallLogEntity,
    targetAddress: String,
    matchedContact: ContactEntity?
): Boolean {
    val targetKey = normalizeCallHistoryKey(targetAddress)
    val logKey = normalizeCallHistoryKey(log.phoneNumber)
    if (targetKey.isNotBlank() && targetKey == logKey) return true
    if (matchedContact != null) {
        if (log.contactInfo.contactId != null && log.contactInfo.contactId == matchedContact.id) return true
        val contactPhoneKey = normalizeCallHistoryKey(matchedContact.phoneNumber)
        if (contactPhoneKey.isNotBlank() && contactPhoneKey == logKey) return true
    }
    return false
}

/**
 * Full-screen Material 3 Call History view for a specific phone number / contact.
 * Shows every incoming, outgoing, missed, and blocked call along with duration,
 * exact timestamp, SIM line, quality MOS, recordings, and instant actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberCallHistoryDialog(
    phoneNumber: String,
    matchedContact: ContactEntity?,
    allLogsForNumber: List<CallLogEntity>,
    recordingsById: Map<Long, CallRecordingEntity>,
    playingRecordingId: Long?,
    onDismiss: () -> Unit,
    onCallSim: (String) -> Unit,
    onAddContact: (() -> Unit)?,
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onReportSpam: (String) -> Unit,
    onDeleteLog: (Long) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val displayName = matchedContact?.displayName?.ifBlank { phoneNumber }
        ?: allLogsForNumber.firstOrNull()?.resolvedContactName?.ifBlank { phoneNumber }
        ?: phoneNumber
    val simNumber = matchedContact?.phoneNumber?.ifBlank { phoneNumber } ?: phoneNumber
    val callingCountry = remember(simNumber, phoneNumber) {
        PhoneCountryResolver.resolveCountryName(context, simNumber.ifBlank { phoneNumber })
    }

    val stats = remember(allLogsForNumber) {
        val incoming = allLogsForNumber.count { it.direction == CallDirection.INCOMING }
        val outgoing = allLogsForNumber.count { it.direction == CallDirection.OUTGOING }
        val missed = allLogsForNumber.count { it.direction == CallDirection.MISSED }
        val totalTalkSec = allLogsForNumber.sumOf { it.durationSec }
        Triple(incoming, outgoing, missed) to totalTalkSec
    }
    val latestMos = remember(allLogsForNumber) {
        allLogsForNumber.firstOrNull { it.mosScore > 0f }?.mosScore ?: 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .testTag("number_call_history_dialog"),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Call History",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1
                            )
                            Text(
                                text = "${allLogsForNumber.size} total ${if (allLogsForNumber.size == 1) "call" else "calls"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_number_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                val toCopy = simNumber.ifBlank { phoneNumber }
                                clipboard.setText(AnnotatedString(toCopy))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = "Copy number"
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
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Caller Profile & Quick Actions Hero Card
                    item {
                        Card(
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ContactAvatar(
                                    name = displayName,
                                    colorIndex = matchedContact?.avatarColorIndex
                                        ?: kotlin.math.abs(displayName.hashCode()) % 5,
                                    photoUri = matchedContact?.photoUri.orEmpty(),
                                    size = 68.dp
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (simNumber.isNotBlank() && simNumber != displayName) {
                                        Text(
                                            text = simNumber,
                                            style = MonoMetricStyle,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (callingCountry.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Public,
                                                contentDescription = "Calling country",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = callingCountry,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    if (!matchedContact?.googleAccountName.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudDone,
                                                contentDescription = "Google Account contact",
                                                tint = AccentSkyBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = matchedContact!!.googleAccountName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = AccentSkyBlue
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    NetworkBadge(networkType = "SIM")
                                    if (latestMos > 0f) {
                                        MosBadge(mosScore = latestMos)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                    ) {
                                        Text(
                                            text = "${allLogsForNumber.size} ${if (allLogsForNumber.size == 1) "call" else "calls"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                if (simNumber.isNotBlank()) {
                                    Button(
                                        onClick = { onCallSim(simNumber) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("history_call_sim_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Call,
                                            contentDescription = "Call SIM",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("SIM Call")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (matchedContact == null && onAddContact != null) {
                                        FilledTonalButton(
                                            onClick = onAddContact,
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("history_add_contact_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PersonAdd,
                                                contentDescription = "Add Contact",
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text("Add Contact")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = { onReportSpam(simNumber.ifBlank { phoneNumber }) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("history_spam_shield_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Shield,
                                            contentDescription = "Spam Shield",
                                            tint = SpamWarningCoral,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text("Spam Shield")
                                    }
                                }
                            }
                        }
                    }

                    // Call Summary Metrics Strip
                    item {
                        val (counts, totalSec) = stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill(
                                label = "INCOMING",
                                value = counts.first.toString(),
                                accent = CallAcceptGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "OUTGOING",
                                value = counts.second.toString(),
                                accent = AccentSkyBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "MISSED",
                                value = counts.third.toString(),
                                accent = CallEndRed,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "TALK TIME",
                                value = formatDuration(totalSec),
                                accent = SimAccentEmerald,
                                modifier = Modifier.weight(1.2f)
                            )
                        }
                    }

                    item {
                        Text(
                            text = "All Phone Call History (${allLogsForNumber.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (allLogsForNumber.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "No Call History for This Number",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Calls placed to or received from this number will appear here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(allLogsForNumber, key = { "hist_${it.id}" }) { entry ->
                            val linkedRec = entry.recordingId?.let { recordingsById[it] }
                            NumberHistoryRowCard(
                                entry = entry,
                                hasRecording = linkedRec != null,
                                isPlayingRecording = linkedRec != null && playingRecordingId == linkedRec.id,
                                onToggleRecording = { linkedRec?.let(onTogglePlayback) },
                                onDelete = { onDeleteLog(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberHistoryRowCard(
    entry: CallLogEntity,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    onToggleRecording: () -> Unit,
    onDelete: () -> Unit
) {
    val dirIcon = when (entry.direction) {
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.MISSED -> Icons.AutoMirrored.Filled.CallMissed
        CallDirection.BLOCKED -> Icons.Filled.Block
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
    }
    val dirColor = when (entry.direction) {
        CallDirection.MISSED, CallDirection.BLOCKED -> CallEndRed
        CallDirection.OUTGOING -> AccentSkyBlue
        CallDirection.INCOMING -> CallAcceptGreen
    }
    val dirLabel = when (entry.direction) {
        CallDirection.OUTGOING -> "Outgoing call"
        CallDirection.INCOMING -> "Incoming call"
        CallDirection.MISSED -> "Missed call"
        CallDirection.BLOCKED -> "Blocked call"
    }
    val fullDate = remember(entry.timestamp) {
        SimpleDateFormat("EEE, MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = dirColor.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = dirIcon,
                        contentDescription = dirLabel,
                        tint = dirColor,
                        modifier = Modifier.size(18.dp)
                    )
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
                    Text(
                        text = dirLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (entry.direction == CallDirection.MISSED || entry.direction == CallDirection.BLOCKED) {
                            dirColor
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    NetworkBadge(networkType = entry.networkType)
                    if (entry.mosScore > 0f) {
                        MosBadge(mosScore = entry.mosScore)
                    }
                    if (entry.encryptedTransport) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted call",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text(
                    text = fullDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = buildString {
                        append(
                            if (entry.durationSec > 0L) "Duration ${formatDuration(entry.durationSec)}"
                            else "Unanswered / 00:00"
                        )
                        if (entry.routeLabel.isNotBlank()) append(" • ${entry.routeLabel}")
                    },
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasRecording) {
                    IconButton(
                        onClick = onToggleRecording,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlayingRecording) Icons.Filled.Stop else Icons.Filled.GraphicEq,
                            contentDescription = "Play call recording",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete call history entry",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
