package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.data.local.SpamRuleEntity
import com.example.telecom.PhoneCountryResolver
import com.example.telecom.SpamAnalyzer
import com.example.ui.components.ContactAvatar
import com.example.ui.components.formatDuration
import com.example.ui.components.formatTimestamp
import com.example.ui.components.normalizeCallHistoryKey
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SpamWarningCoral

/**
 * Deduplicated recent call summary where each phone number appears exactly once,
 * preserving its most recent call log along with total and missed call counts.
 */
internal data class GroupedRecentCall(
    val numberKey: String,
    val latestLog: CallLogEntity,
    val logs: List<CallLogEntity>,
    val totalCalls: Int,
    val missedCalls: Int,
    val blockedCalls: Int
)

/**
 * Returns true if the call log represents a spam or blocked number (either logged as blocked/spam
 * or matching one of the user's active auto-block rules).
 */
internal fun isSpamOrBlockedCallLog(
    log: CallLogEntity,
    blockedRules: List<SpamRuleEntity>
): Boolean {
    return log.direction == CallDirection.BLOCKED ||
        log.spamScore >= 50 ||
        (blockedRules.isNotEmpty() && SpamAnalyzer.findBlockingRule(log.phoneNumber, blockedRules) != null)
}

/**
 * Groups call logs by normalized phone number so each number appears only once,
 * sorted newest-first by the representative log's timestamp.
 */
internal fun groupCallLogsByUniqueNumber(
    logs: List<CallLogEntity>,
    preferLatestMissedAsRepresentative: Boolean = false
): List<GroupedRecentCall> {
    if (logs.isEmpty()) return emptyList()
    return logs
        .groupBy { log ->
            normalizeCallHistoryKey(log.phoneNumber).ifBlank { "id_${log.id}" }
        }
        .map { (key, groupLogs) ->
            val sorted = groupLogs.sortedByDescending { it.timestamp }
            val missedCount = sorted.count { it.direction == CallDirection.MISSED }
            val blockedCount = sorted.count { it.direction == CallDirection.BLOCKED }
            val representative = if (preferLatestMissedAsRepresentative) {
                sorted.firstOrNull { it.direction == CallDirection.MISSED } ?: sorted.first()
            } else {
                sorted.first()
            }
            GroupedRecentCall(
                numberKey = key,
                latestLog = representative,
                logs = sorted,
                totalCalls = sorted.size,
                missedCalls = missedCount,
                blockedCalls = blockedCount
            )
        }
        .sortedByDescending { it.latestLog.timestamp }
}

/**
 * Normalizes a persisted routeLabel (e.g. "SIM 1 • Airtel", "SIM 2", "Device SIM")
 * into a clean display label and slot number (1, 2, or null).
 */
internal fun parseSimSlotInfo(routeLabel: String): Pair<Int?, String> {
    val trimmed = routeLabel.trim()
    val lower = trimmed.lowercase()
    return when {
        lower.contains("sim 2") || lower.contains("sim2") || lower.contains("slot 2") -> {
            2 to trimmed
        }
        lower.contains("sim 1") || lower.contains("sim1") || lower.contains("slot 1") -> {
            1 to trimmed
        }
        trimmed.isBlank() ||
            lower == "device sim" ||
            lower == "sim cellular" ||
            lower == "phone default sim" ||
            lower == "synced" -> {
            null to "SIM"
        }
        else -> null to trimmed
    }
}

/**
 * Compact SIM indicator badge showing which SIM (e.g. SIM 1, SIM 2) was used for the call.
 */
@Composable
internal fun SimSlotBadge(
    routeLabel: String,
    compact: Boolean = true,
    modifier: Modifier = Modifier
) {
    val (slotNumber, fullLabel) = remember(routeLabel) { parseSimSlotInfo(routeLabel) }
    val displayText = remember(slotNumber, fullLabel, compact) {
        if (compact) {
            when (slotNumber) {
                1 -> "SIM 1"
                2 -> "SIM 2"
                else -> fullLabel.substringBefore("•").trim().ifBlank { "SIM" }
            }
        } else {
            fullLabel.ifBlank { "SIM" }
        }
    }
    val accentColor = when (slotNumber) {
        1 -> AccentSkyBlue
        2 -> CallAcceptGreen
        else -> MaterialTheme.colorScheme.secondary
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = accentColor.copy(alpha = 0.14f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 5.dp else 7.dp,
                vertical = if (compact) 1.dp else 3.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SimCard,
                contentDescription = "Called using $displayText",
                tint = accentColor,
                modifier = Modifier.size(if (compact) 11.dp else 12.dp)
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun RecentsEmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
internal fun RecordedCallCard(
    recording: CallRecordingEntity,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onCallBack: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val callingCountry = remember(recording.phoneNumber) {
        PhoneCountryResolver.resolveCountryName(context, recording.phoneNumber)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recorded_menu_card_${recording.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.testTag("play_recording_${recording.id}")
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
                        text = recording.displayName.ifBlank { recording.phoneNumber },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (recording.autoRecorded) {
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
                        imageVector = if (recording.encryptedAtRest) Icons.Filled.Lock else Icons.Filled.Folder,
                        contentDescription = if (recording.encryptedAtRest) "Encrypted recording" else "Saved in App Internal Storage",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }
                if (recording.displayName.isNotBlank() && recording.phoneNumber.isNotBlank()) {
                    Text(
                        text = buildString {
                            append(recording.phoneNumber)
                            if (callingCountry.isNotBlank()) append(" • $callingCountry")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else if (callingCountry.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = "Calling country",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = callingCountry,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = "${formatDuration(recording.durationSec)} • ${recording.fileSizeBytes / 1024} KB • ${formatTimestamp(recording.createdAt)}",
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (recording.phoneNumber.isNotBlank()) {
                IconButton(onClick = onCallBack) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete recording",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CallNoteMenuCard(
    log: CallLogEntity,
    matchedContact: ContactEntity?,
    onEditNote: () -> Unit,
    onCallBack: () -> Unit
) {
    val context = LocalContext.current
    val resolvedName = matchedContact?.displayName?.ifBlank { log.resolvedContactName } ?: log.resolvedContactName
    val callingCountry = remember(log.phoneNumber) {
        PhoneCountryResolver.resolveCountryName(context, log.phoneNumber)
    }
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

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEditNote)
            .testTag("notes_menu_card_${log.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(42.dp)) {
                    if (matchedContact != null) {
                        ContactAvatar(
                            name = matchedContact.displayName,
                            colorIndex = matchedContact.avatarColorIndex,
                            photoUri = matchedContact.photoUri,
                            size = 40.dp
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(16.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = dirIcon,
                                    contentDescription = log.direction.name,
                                    tint = dirColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = dirColor.copy(alpha = 0.14f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = dirIcon,
                                    contentDescription = log.direction.name,
                                    tint = dirColor,
                                    modifier = Modifier.size(18.dp)
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
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        SimSlotBadge(routeLabel = log.routeLabel, compact = true)
                    }
                    Text(
                        text = buildString {
                            if (resolvedName.isNotBlank()) append("${log.phoneNumber} • ")
                            if (callingCountry.isNotBlank()) append("$callingCountry • ")
                            append(formatTimestamp(log.timestamp))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onEditNote,
                    modifier = Modifier.testTag("notes_menu_edit_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit call note",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onCallBack,
                    modifier = Modifier.testTag("notes_menu_call_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call back",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditNote)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = "Call note",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = log.quickNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun BlockedNumberSpamMenuCard(
    rule: SpamRuleEntity,
    onUnblock: () -> Unit
) {
    val context = LocalContext.current
    val callingCountry = remember(rule.pattern) {
        PhoneCountryResolver.resolveCountryName(context, rule.pattern)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, CallEndRed.copy(alpha = 0.30f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("spam_menu_blocked_rule_${rule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CallEndRed.copy(alpha = 0.14f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Block,
                        contentDescription = "Blocked number",
                        tint = CallEndRed,
                        modifier = Modifier.size(20.dp)
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
                    Text(
                        text = rule.pattern,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = CallEndRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SpamWarningCoral.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "BLOCKED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpamWarningCoral,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = buildString {
                        append(rule.label.ifBlank { "Blocked Number" })
                        if (callingCountry.isNotBlank()) append(" • $callingCountry")
                        if (rule.matchCount > 0) append(" • ${rule.matchCount} rejected")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onUnblock,
                modifier = Modifier.testTag("spam_menu_unblock_${rule.id}")
            ) {
                Text("Unblock", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
