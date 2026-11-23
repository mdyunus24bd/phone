package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.theme.CallEndRed
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
    onShareRecording: (CallRecordingEntity) -> Unit = {},
    onDeleteLog: (Long) -> Unit,
    onImportDeviceContacts: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ContactFilter.ALL) }
    var expandedContactId by remember { mutableStateOf<Long?>(null) }
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
                        isExpanded = expandedContactId == contact.id,
                        onToggleExpand = {
                            expandedContactId = if (expandedContactId == contact.id) null else contact.id
                        },
                        onOpenHistory = { historyContact = contact },
                        onCallSim = {
                            if (contact.phoneNumber.isNotBlank()) {
                                onCallContact(contact.phoneNumber)
                            }
                        },
                        onToggleFavorite = { onToggleFavorite(contact) },
                        onEdit = { onOpenEditContact(contact) },
                        onDelete = {
                            if (expandedContactId == contact.id) {
                                expandedContactId = null
                            }
                            onDeleteContact(contact)
                        }
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
            onShareRecording = onShareRecording,
            onDeleteLog = onDeleteLog
        )
    }
}

@Composable
private fun ContactCard(
    contact: ContactEntity,
    decryptedNote: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenHistory: () -> Unit,
    onCallSim: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isExpanded) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .testTag("contact_card_${contact.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Clean default contact row: Avatar, Name/Number, and icon-only SIM Call button on the right
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
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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

                // Right-side icon-only SIM Call button
                if (contact.phoneNumber.isNotBlank()) {
                    IconButton(
                        onClick = onCallSim,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .testTag("contact_sim_call_${contact.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "SIM Call ${contact.displayName}",
                            tint = SimAccentEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Expanded inside-contact options (History, Favorites, Edit, Delete) shown when clicked
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
                        .testTag("contact_expanded_options_${contact.id}"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ContactExpandedActionTile(
                            icon = Icons.Filled.History,
                            label = "History",
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = onOpenHistory,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contact_option_history_${contact.id}")
                        )
                        ContactExpandedActionTile(
                            icon = if (contact.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            label = "Favorite",
                            tint = if (contact.favorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contact_option_favorite_${contact.id}")
                        )
                        ContactExpandedActionTile(
                            icon = Icons.Filled.Edit,
                            label = "Edit",
                            tint = MaterialTheme.colorScheme.onSurface,
                            onClick = onEdit,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contact_option_edit_${contact.id}")
                        )
                        ContactExpandedActionTile(
                            icon = Icons.Filled.DeleteOutline,
                            label = "Delete",
                            tint = CallEndRed,
                            onClick = onDelete,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contact_option_delete_${contact.id}")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactExpandedActionTile(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
