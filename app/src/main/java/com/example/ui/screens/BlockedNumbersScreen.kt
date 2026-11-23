package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneDisabled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.SpamRuleEntity
import com.example.telecom.PhoneCountryResolver
import com.example.telecom.SpamAnalyzer
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent

private data class QuickBlockCandidate(
    val phoneNumber: String,
    val displayName: String,
    val subtitle: String
)

/**
 * Dedicated full-screen Blocked Numbers settings page where users can manually add,
 * pick from recents/contacts, search, and unblock phone numbers that are automatically
 * rejected by VoxLine's InCallService.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedNumbersScreen(
    blockedRules: List<SpamRuleEntity>,
    contacts: List<ContactEntity>,
    callLogs: List<CallLogEntity>,
    onBlockNumber: (phoneNumber: String, label: String) -> Unit,
    onUnblockNumber: (SpamRuleEntity) -> Unit,
    onClearAllBlocked: () -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var numberInput by remember { mutableStateOf("") }
    var labelInput by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showRecentsPicker by remember { mutableStateOf(false) }
    var showContactsPicker by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val totalRejectedCount = remember(blockedRules) {
        blockedRules.sumOf { it.matchCount }
    }

    val contactNameByDigits = remember(contacts) {
        buildMap {
            contacts.forEach { contact ->
                val digits = contact.phoneNumber.filter { it.isDigit() }
                if (digits.isNotEmpty()) {
                    put(digits, contact.displayName)
                    if (digits.length >= 10) {
                        put(digits.takeLast(10), contact.displayName)
                    }
                }
            }
        }
    }

    val previewCountry = remember(numberInput) {
        val clean = numberInput.trim()
        if (clean.count { it.isDigit() } >= 3) {
            PhoneCountryResolver.resolveCountryName(context, clean)
        } else {
            ""
        }
    }

    val isInputAlreadyBlocked = remember(numberInput, blockedRules) {
        val clean = numberInput.trim()
        clean.isNotEmpty() && SpamAnalyzer.findBlockingRule(clean, blockedRules) != null
    }

    val filteredBlocked = remember(blockedRules, searchQuery, contactNameByDigits) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            blockedRules
        } else {
            val qDigits = q.filter { it.isDigit() }
            blockedRules.filter { rule ->
                val digits = rule.pattern.filter { it.isDigit() }
                val matchedName = contactNameByDigits[digits]
                    ?: if (digits.length >= 10) contactNameByDigits[digits.takeLast(10)] else null
                rule.pattern.contains(q, ignoreCase = true) ||
                    rule.label.contains(q, ignoreCase = true) ||
                    (matchedName?.contains(q, ignoreCase = true) == true) ||
                    (qDigits.isNotEmpty() && digits.contains(qDigits))
            }
        }
    }

    val recentCandidates = remember(callLogs, blockedRules) {
        val seenKeys = mutableSetOf<String>()
        buildList {
            callLogs.forEach { log ->
                val num = log.phoneNumber.trim()
                val digits = num.filter { it.isDigit() }
                if (num.isNotEmpty() && digits.isNotEmpty()) {
                    val key = if (digits.length >= 10) digits.takeLast(10) else digits
                    if (seenKeys.add(key) && SpamAnalyzer.findBlockingRule(num, blockedRules) == null) {
                        add(
                            QuickBlockCandidate(
                                phoneNumber = num,
                                displayName = log.resolvedContactName.ifBlank { num },
                                subtitle = formatTimestamp(log.timestamp)
                            )
                        )
                    }
                }
            }
        }
    }

    val contactCandidates = remember(contacts, blockedRules) {
        contacts.mapNotNull { contact ->
            val num = contact.phoneNumber.trim()
            if (num.isEmpty() || SpamAnalyzer.findBlockingRule(num, blockedRules) != null) {
                null
            } else {
                QuickBlockCandidate(
                    phoneNumber = num,
                    displayName = contact.displayName.ifBlank { num },
                    subtitle = contact.label
                )
            }
        }
    }

    val submitManualBlock: () -> Unit = {
        val clean = numberInput.trim()
        if (clean.any { it.isDigit() }) {
            onBlockNumber(clean, labelInput.trim())
            numberInput = ""
            labelInput = ""
            focusManager.clearFocus()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .testTag("blocked_numbers_screen"),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Blocked Numbers") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("blocked_numbers_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings"
                        )
                    }
                },
                actions = {
                    if (blockedRules.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearAllDialog = true },
                            modifier = Modifier.testTag("clear_all_blocked_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Clear all blocked numbers",
                                tint = CallEndRed
                            )
                        }
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status Hero Summary Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            CallEndRed.copy(alpha = 0.14f),
                                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.0f)
                                        )
                                    )
                                )
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(CallEndRed.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhoneDisabled,
                                        contentDescription = "Auto-reject shield active",
                                        tint = CallEndRed,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Automatic Call Rejection",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Incoming calls from any number below are immediately declined before ringing and logged in Recents.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "BLOCKED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${blockedRules.size}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = CallEndRed
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "CALLS REJECTED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "$totalRejectedCount",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (totalRejectedCount > 0) QualityExcellent
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Add Phone Number Form Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Add Number to Block",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            OutlinedTextField(
                                value = numberInput,
                                onValueChange = { numberInput = it },
                                label = { Text("Phone number or prefix*") },
                                placeholder = { Text("e.g. +1 555 019 9988 or +1800*") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Next
                                ),
                                trailingIcon = {
                                    if (numberInput.isNotEmpty()) {
                                        IconButton(onClick = { numberInput = "" }) {
                                            Icon(
                                                imageVector = Icons.Filled.Clear,
                                                contentDescription = "Clear number"
                                            )
                                        }
                                    }
                                },
                                supportingText = {
                                    when {
                                        isInputAlreadyBlocked -> {
                                            Text(
                                                text = "This number is already blocked.",
                                                color = CallEndRed
                                            )
                                        }
                                        previewCountry.isNotBlank() -> {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Language,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = previewCountry,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        else -> {
                                            Text("Matches exact numbers, local/international formats, or prefix*")
                                        }
                                    }
                                },
                                isError = isInputAlreadyBlocked,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blocked_number_input")
                            )

                            OutlinedTextField(
                                value = labelInput,
                                onValueChange = { labelInput = it },
                                label = { Text("Reason / Label (optional)") },
                                placeholder = { Text("e.g. Telemarketer, Unwanted") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitManualBlock() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("blocked_number_label_input")
                            )

                            Button(
                                onClick = submitManualBlock,
                                enabled = numberInput.any { it.isDigit() } && !isInputAlreadyBlocked,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CallEndRed,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("add_blocked_number_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Block Number")
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showRecentsPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("pick_block_from_recents_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("From Recents", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = { showContactsPicker = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("pick_block_from_contacts_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Contacts,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("From Contacts", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Search bar for larger blocklists
                item {
                    AnimatedVisibility(visible = blockedRules.size >= 4) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search blocked numbers or labels") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search blocked numbers"
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear search"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("blocked_numbers_search_input")
                        )
                    }
                }

                // Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Blocked List (${filteredBlocked.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (blockedRules.isNotEmpty()) {
                            Text(
                                text = "Auto-reject active",
                                style = MaterialTheme.typography.labelSmall,
                                color = QualityExcellent
                            )
                        }
                    }
                }

                // Empty State or Blocked List Items
                if (blockedRules.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Shield,
                                        contentDescription = "No blocked numbers",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = "No Blocked Numbers Yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Enter a phone number above or pick one from Recents or Contacts to automatically reject unwanted calls.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else if (filteredBlocked.isEmpty()) {
                    item {
                        Text(
                            text = "No blocked numbers match \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                } else {
                    items(filteredBlocked, key = { it.id }) { rule ->
                        val digits = remember(rule.pattern) { rule.pattern.filter { it.isDigit() } }
                        val contactName = remember(digits, contactNameByDigits) {
                            contactNameByDigits[digits]
                                ?: if (digits.length >= 10) contactNameByDigits[digits.takeLast(10)] else null
                        }
                        val countryName = remember(rule.pattern) {
                            PhoneCountryResolver.resolveCountryName(context, rule.pattern)
                        }

                        BlockedNumberRowCard(
                            rule = rule,
                            contactName = contactName,
                            countryName = countryName,
                            onUnblock = { onUnblockNumber(rule) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showRecentsPicker) {
        QuickPickBlockDialog(
            title = "Block from Recent Calls",
            emptyMessage = "All recent call numbers are already blocked or no call logs are available.",
            candidates = recentCandidates,
            onPick = { candidate ->
                onBlockNumber(candidate.phoneNumber, candidate.displayName.takeIf { it != candidate.phoneNumber }.orEmpty())
                showRecentsPicker = false
            },
            onDismiss = { showRecentsPicker = false }
        )
    }

    if (showContactsPicker) {
        QuickPickBlockDialog(
            title = "Block from Contacts",
            emptyMessage = "All saved contacts are already blocked or no contacts are available.",
            candidates = contactCandidates,
            onPick = { candidate ->
                onBlockNumber(candidate.phoneNumber, candidate.displayName)
                showContactsPicker = false
            },
            onDismiss = { showContactsPicker = false }
        )
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Unblock All Numbers?") },
            text = {
                Text("This removes all ${blockedRules.size} numbers from your Blocked Numbers list. Calls from these numbers will ring normally again.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDialog = false
                        onClearAllBlocked()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CallEndRed),
                    modifier = Modifier.testTag("confirm_clear_all_blocked_button")
                ) {
                    Text("Unblock All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun BlockedNumberRowCard(
    rule: SpamRuleEntity,
    contactName: String?,
    countryName: String,
    onUnblock: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("blocked_number_card_${rule.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CallEndRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = "Blocked number",
                    tint = CallEndRed,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = rule.pattern,
                    style = MonoMetricStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val headlineSub = buildList {
                    if (!contactName.isNullOrBlank()) add(contactName)
                    if (rule.label.isNotBlank() && !rule.label.equals(contactName, ignoreCase = true)) {
                        add(rule.label)
                    }
                    if (countryName.isNotBlank()) add(countryName)
                }.joinToString(" • ")

                if (headlineSub.isNotBlank()) {
                    Text(
                        text = headlineSub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val metaText = buildString {
                    append("Added ${formatTimestamp(rule.createdAt)}")
                    if (rule.matchCount > 0) {
                        append(" • ${rule.matchCount} rejected")
                    }
                }
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (rule.matchCount > 0) CallEndRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = onUnblock,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("unblock_number_${rule.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.LockOpen,
                    contentDescription = "Unblock ${rule.pattern}",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Unblock", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun QuickPickBlockDialog(
    title: String,
    emptyMessage: String,
    candidates: List<QuickBlockCandidate>,
    onPick: (QuickBlockCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(candidates, key = { "${it.phoneNumber}_${it.displayName}" }) { candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(candidate) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = candidate.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (candidate.displayName == candidate.phoneNumber) {
                                        candidate.subtitle
                                    } else {
                                        "${candidate.phoneNumber} • ${candidate.subtitle}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Block ${candidate.phoneNumber}",
                                tint = CallEndRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
