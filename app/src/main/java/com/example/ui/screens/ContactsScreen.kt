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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.ContactEntity
import com.example.ui.components.ContactAvatar
import com.example.ui.components.NumberCallHistoryDialog
import com.example.ui.components.formatTimestamp
import com.example.ui.components.matchesCallHistoryTarget
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.SimAccentEmerald

private enum class ContactFilter(val label: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    GOOGLE("Google")
}

@Composable
fun ContactsScreen(
    contacts: List<ContactEntity>,
    callLogs: List<CallLogEntity>,
    recordings: List<CallRecordingEntity>,
    playingRecordingId: Long?,
    hasContactsPermission: Boolean,
    isSyncingContacts: Boolean,
    lastAutoSyncTimestamp: Long,
    decryptNote: (String) -> String,
    onOpenAddContact: () -> Unit,
    onOpenEditContact: (ContactEntity) -> Unit,
    onToggleFavorite: (ContactEntity) -> Unit,
    onDeleteContact: (ContactEntity) -> Unit,
    onCallContact: (String) -> Unit,
    onTogglePlayback: (CallRecordingEntity) -> Unit,
    onReportSpam: (String) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onImportDeviceContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ContactFilter.ALL) }
    var historyContact by remember { mutableStateOf<ContactEntity?>(null) }
    val recordingsById = remember(recordings) { recordings.associateBy { it.id } }

    val favorites = remember(contacts) { contacts.filter { it.favorite } }
    val filtered = remember(contacts, searchQuery, filter) {
        contacts.filter { c ->
            val matchesFilter = when (filter) {
                ContactFilter.ALL -> true
                ContactFilter.FAVORITES -> c.favorite
                ContactFilter.GOOGLE -> c.googleAccountName.isNotBlank()
            }
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                c.displayName.contains(q, ignoreCase = true) ||
                c.phoneNumber.contains(q, ignoreCase = true) ||
                c.googleAccountName.contains(q, ignoreCase = true) ||
                c.label.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Contacts",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                if (hasContactsPermission) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = CallAcceptGreen.copy(alpha = 0.16f),
                                        modifier = Modifier.testTag("contacts_auto_sync_badge")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Auto-Sync active",
                                                tint = CallAcceptGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "Auto-Sync",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CallAcceptGreen
                                            )
                                        }
                                    }
                                }
                            }
                            Text(
                                text = when {
                                    isSyncingContacts -> "Auto-importing device contacts..."
                                    hasContactsPermission && lastAutoSyncTimestamp > 0L ->
                                        "${contacts.size} SIM contacts • Synced ${formatTimestamp(lastAutoSyncTimestamp)}"
                                    hasContactsPermission -> "${contacts.size} SIM contacts • Live auto-import"
                                    else -> "${contacts.size} local entries • Tap Enable to auto-import"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.testTag("contacts_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onOpenAddContact,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("add_contact_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PersonAdd,
                                contentDescription = "Add new contact",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("New Contact")
                        }
                        OutlinedButton(
                            onClick = onImportDeviceContacts,
                            enabled = !isSyncingContacts,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("import_device_contacts_button")
                        ) {
                            if (isSyncingContacts) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = "Sync device contacts",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isSyncingContacts -> "Syncing"
                                    hasContactsPermission -> "Sync"
                                    else -> "Enable Sync"
                                }
                            )
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("contacts_search_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Search name, SIM number, or label") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search contacts"
                        )
                    }
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ContactFilter.entries) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text(f.label) }
                        )
                    }
                }
            }

            // Favorites Speed-Dial Horizontal Rail
            if (favorites.isNotEmpty() && searchQuery.isBlank() && filter == ContactFilter.ALL) {
                item {
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(favorites, key = { "fav_${it.id}" }) { fav ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .width(120.dp)
                                    .clickable {
                                        if (fav.phoneNumber.isNotBlank()) {
                                            onCallContact(fav.phoneNumber)
                                        }
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ContactAvatar(
                                        name = fav.displayName,
                                        colorIndex = fav.avatarColorIndex,
                                        photoUri = fav.photoUri,
                                        size = 46.dp
                                    )
                                    Text(
                                        text = fav.displayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = fav.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
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
                                imageVector = Icons.Filled.Contacts,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text(
                                text = "No Contacts Found",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Add a contact with a SIM phone number, or import contacts directly from your Android address book.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { contact ->
                    val decryptedNote = remember(contact.encryptedNotes) {
                        decryptNote(contact.encryptedNotes)
                    }
                    ContactCard(
                        contact = contact,
                        decryptedNote = decryptedNote,
                        onOpenHistory = { historyContact = contact },
                        onCallSim = {
                            if (contact.phoneNumber.isNotBlank()) {
                                onCallContact(contact.phoneNumber)
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(contact) },
                        onEdit = { onOpenEditContact(contact) },
                        onDelete = { onDeleteContact(contact) }
                    )
                }
            }
        }
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
private fun ContactCard(
    contact: ContactEntity,
    decryptedNote: String,
    onOpenHistory: () -> Unit,
    onCallSim: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenHistory)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ContactAvatar(
                    name = contact.displayName,
                    colorIndex = contact.avatarColorIndex,
                    photoUri = contact.photoUri
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = contact.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = contact.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (contact.phoneNumber.isNotBlank()) {
                        Text(
                            text = contact.phoneNumber,
                            style = MonoMetricStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (contact.googleAccountName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = "Saved to Google Account",
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = contact.googleAccountName,
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentSkyBlue,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (contact.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (contact.favorite) "Remove from favorites" else "Add to favorites",
                        tint = if (contact.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (decryptedNote.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Encrypted private note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = decryptedNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (contact.phoneNumber.isNotBlank()) {
                        FilledTonalButton(
                            onClick = onCallSim,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Call,
                                contentDescription = "Call SIM",
                                tint = SimAccentEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("SIM Call", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Row {
                    IconButton(onClick = onOpenHistory, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "View contact call history",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit contact",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete contact",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
