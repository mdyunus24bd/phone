package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.ui.components.ContactAvatar
import com.example.ui.components.NumberCallHistoryDialog
import com.example.ui.components.matchesCallHistoryTarget
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald

@Composable
fun FavoritesScreen(
    contacts: List<ContactEntity>,
    callLogs: List<CallLogEntity>,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    onCallContact: (String) -> Unit,
    onToggleFavorite: (ContactEntity) -> Unit,
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onReportSpam: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddFavoritePicker by remember { mutableStateOf(false) }
    var historyContact by remember { mutableStateOf<ContactEntity?>(null) }

    val favorites = remember(contacts) { contacts.filter { it.favorite } }
    val nonFavorites = remember(contacts) { contacts.filter { !it.favorite } }
    val favoriteRows = remember(favorites) { favorites.chunked(2) }
    val recordingsById = remember(recordings) { recordings.associateBy { it.id } }

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
                            text = "Favorites",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "${favorites.size} speed-dial ${if (favorites.size == 1) "contact" else "contacts"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { showAddFavoritePicker = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("add_favorite_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add to Favorites",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Add")
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("favorites_settings_button")
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

            if (favorites.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = "No Favorite Contacts Yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Star your frequent SIM contacts for one-tap speed dialing with contact photos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (nonFavorites.isNotEmpty()) {
                                FilledTonalButton(
                                    onClick = { showAddFavoritePicker = true },
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose Favorites")
                                }
                            }
                        }
                    }
                }
            } else {
                items(favoriteRows, key = { row -> row.joinToString("_") { it.id.toString() } }) { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { contact ->
                            FavoriteSpeedDialCard(
                                contact = contact,
                                onCallSim = {
                                    if (contact.phoneNumber.isNotBlank()) {
                                        onCallContact(contact.phoneNumber)
                                    }
                                },
                                onOpenHistory = { historyContact = contact },
                                onUnstar = { onToggleFavorite(contact) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    if (showAddFavoritePicker) {
        AlertDialog(
            onDismissRequest = { showAddFavoritePicker = false },
            title = { Text("Add to Favorites") },
            text = {
                if (nonFavorites.isEmpty()) {
                    Text(
                        text = if (contacts.isEmpty()) {
                            "Add or sync contacts first, then pin your favorites here."
                        } else {
                            "All your contacts are already in Favorites."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(320.dp)
                    ) {
                        items(nonFavorites, key = { "pick_${it.id}" }) { c ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleFavorite(c) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ContactAvatar(
                                    name = c.displayName,
                                    colorIndex = c.avatarColorIndex,
                                    photoUri = c.photoUri,
                                    size = 40.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = c.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = c.phoneNumber,
                                        style = MonoMetricStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Outlined.StarBorder,
                                    contentDescription = "Pin favorite",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddFavoritePicker = false }) {
                    Text("Done")
                }
            }
        )
    }

    historyContact?.let { selected ->
        val primaryAddr = selected.phoneNumber
        val numberHistory = remember(callLogs, selected) {
            callLogs.filter { matchesCallHistoryTarget(it, primaryAddr, selected) }
        }
        NumberCallHistoryDialog(
            phoneNumber = primaryAddr,
            matchedContact = selected,
            allLogsForNumber = numberHistory,
            recordingsById = recordingsById,
            playingRecordingId = playingRecordingId,
            onDismiss = { historyContact = null },
            onCallSim = { num ->
                historyContact = null
                onCallContact(num)
            },
            onAddContact = null,
            onTogglePlayback = onTogglePlayback,
            onReportSpam = onReportSpam,
            onDeleteLog = onDeleteLog
        )
    }
}

@Composable
private fun FavoriteSpeedDialCard(
    contact: ContactEntity,
    onCallSim: () -> Unit,
    onOpenHistory: () -> Unit,
    onUnstar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.clickable {
            if (contact.phoneNumber.isNotBlank()) onCallSim()
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "View call history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onUnstar,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Remove from favorites",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ContactAvatar(
                name = contact.displayName,
                colorIndex = contact.avatarColorIndex,
                photoUri = contact.photoUri,
                size = 64.dp
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = contact.phoneNumber,
                    style = MonoMetricStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (contact.googleAccountName.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudDone,
                            contentDescription = "Google synced",
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Google",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentSkyBlue
                        )
                    }
                }
            }

            if (contact.phoneNumber.isNotBlank()) {
                FilledTonalButton(
                    onClick = onCallSim,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Call,
                        contentDescription = "Call SIM",
                        tint = SimAccentEmerald,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Call", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
