package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ContactEntity
import com.example.data.repository.GoogleContactAccount
import com.example.ui.components.ContactAvatar
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen

/**
 * Payload produced when saving a contact from Contacts, Recents, or Keypad.
 */
data class ContactSaveDraft(
    val id: Long = 0L,
    val displayName: String,
    val phoneNumber: String,
    val label: String,
    val favorite: Boolean,
    val notes: String,
    val photoUri: String,
    val saveToGoogleAccount: Boolean,
    val googleAccountName: String
)

private enum class ContactSaveDestination {
    GOOGLE_ACCOUNT,
    LOCAL_VAULT
}

private val QuickContactLabels = listOf("Mobile", "Work", "Home", "Family", "VIP")

/**
 * Full-screen Material Design 3 Add / Edit Contact page supporting:
 * - Zero-permission Android Photo Picker (`PickVisualMedia`) for contact photos
 * - Pre-filled phone values when adding a new contact from Recents or Keypad
 * - Saving directly to a Google Account on the device or encrypted local vault
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(
    initial: ContactEntity?,
    initialNote: String,
    prefillPhone: String = "",
    prefillName: String = "",
    googleAccounts: List<GoogleContactAccount>,
    hasWriteContactsPermission: Boolean,
    onOpenAddGoogleAccount: () -> Unit,
    onBack: () -> Unit,
    onSave: (ContactSaveDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var name by remember(initial, prefillName) {
        mutableStateOf(initial?.displayName?.ifBlank { prefillName } ?: prefillName)
    }
    var phone by remember(initial, prefillPhone) {
        mutableStateOf(initial?.phoneNumber?.ifBlank { prefillPhone } ?: prefillPhone)
    }
    var label by remember(initial) { mutableStateOf(initial?.label ?: "Mobile") }
    var favorite by remember(initial) { mutableStateOf(initial?.favorite ?: false) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }
    var photoUri by remember(initial) { mutableStateOf(initial?.photoUri.orEmpty()) }

    val defaultGoogleEmail = remember(initial, googleAccounts) {
        initial?.googleAccountName?.takeIf { it.isNotBlank() }
            ?: googleAccounts.firstOrNull()?.accountName.orEmpty()
    }
    var selectedGoogleEmail by remember(defaultGoogleEmail) { mutableStateOf(defaultGoogleEmail) }
    var saveDestination by remember(initial, googleAccounts) {
        mutableStateOf(
            if (initial == null || initial.googleAccountName.isNotBlank() || googleAccounts.isNotEmpty()) {
                ContactSaveDestination.GOOGLE_ACCOUNT
            } else {
                ContactSaveDestination.LOCAL_VAULT
            }
        )
    }

    val canSave = name.isNotBlank() && phone.isNotBlank()
    val submitContact: () -> Unit = {
        val saveToGoogle = saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT
        onSave(
            ContactSaveDraft(
                id = initial?.id ?: 0L,
                displayName = name.trim(),
                phoneNumber = phone.trim(),
                label = label.trim().ifBlank { "Mobile" },
                favorite = favorite,
                notes = note,
                photoUri = photoUri,
                saveToGoogleAccount = saveToGoogle,
                googleAccountName = if (saveToGoogle) selectedGoogleEmail else ""
            )
        )
    }

    // Zero-permission Android Photo Picker (Google Play policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri ->
        if (pickedUri != null) {
            photoUri = pickedUri.toString()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("contact_editor_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initial == null) "Add New Contact" else "Edit Contact",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("contact_editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = submitContact,
                        enabled = canSave,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("save_contact_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Contact Photo Picker Hero Card
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                                .testTag("pick_contact_photo_avatar"),
                            contentAlignment = Alignment.Center
                        ) {
                            ContactAvatar(
                                name = name.ifBlank { phone.ifBlank { "?" } },
                                colorIndex = kotlin.math.abs(name.ifBlank { phone }.hashCode()) % 5,
                                photoUri = photoUri,
                                size = 100.dp
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 4.dp,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = "Choose contact photo",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("pick_contact_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (photoUri.isBlank()) "Add Photo" else "Change Photo",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            if (photoUri.isNotBlank()) {
                                OutlinedButton(
                                    onClick = { photoUri = "" },
                                    modifier = Modifier
                                        .height(44.dp)
                                        .testTag("remove_contact_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove photo",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                // Contact Details Form Card
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Contact Details",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name") },
                            placeholder = { Text("Enter contact name") },
                            leadingIcon = {
                                Icon(Icons.Filled.Person, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_name_input")
                        )

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("SIM Phone Number") },
                            placeholder = { Text("+1 555 019 2834") },
                            leadingIcon = {
                                Icon(Icons.Filled.Call, contentDescription = null)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_phone_input")
                        )

                        // Quick Label Chips + Custom Group Input
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Phone Label / Group",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(QuickContactLabels) { preset ->
                                    FilterChip(
                                        selected = label.equals(preset, ignoreCase = true),
                                        onClick = { label = preset },
                                        label = { Text(preset) },
                                        modifier = Modifier.testTag("contact_label_chip_${preset.lowercase()}")
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = label,
                                onValueChange = { label = it },
                                label = { Text("Custom Group / Label") },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("contact_label_input")
                            )
                        }

                        OutlinedTextField(
                            value = note,
                            onValueChange = { note = it },
                            label = { Text("Encrypted Private Note (AES-256)") },
                            placeholder = { Text("Optional private note stored encrypted on device") },
                            leadingIcon = {
                                Icon(Icons.Filled.Lock, contentDescription = null)
                            },
                            minLines = 2,
                            maxLines = 4,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("contact_note_input")
                        )
                    }
                }

                // Save Destination: Google Account vs Local Encrypted Vault
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = BorderStroke(
                        1.dp,
                        if (saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT) {
                            AccentSkyBlue.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDone,
                                contentDescription = null,
                                tint = AccentSkyBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Save Contact To",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (googleAccounts.isNotEmpty()) {
                            googleAccounts.forEach { account ->
                                val isSelected =
                                    saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT &&
                                        selectedGoogleEmail == account.accountName
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) {
                                        AccentSkyBlue.copy(alpha = 0.12f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerLow
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            saveDestination = ContactSaveDestination.GOOGLE_ACCOUNT
                                            selectedGoogleEmail = account.accountName
                                        }
                                        .testTag("save_google_account_${account.accountName}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                saveDestination = ContactSaveDestination.GOOGLE_ACCOUNT
                                                selectedGoogleEmail = account.accountName
                                            }
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = AccentSkyBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Google Account",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                            Text(
                                                text = account.accountName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isSelected && hasWriteContactsPermission) {
                                            Icon(
                                                imageVector = Icons.Filled.CheckCircle,
                                                contentDescription = "Direct sync ready",
                                                tint = CallAcceptGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT) {
                                    AccentSkyBlue.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        saveDestination = ContactSaveDestination.GOOGLE_ACCOUNT
                                        selectedGoogleEmail = ""
                                    }
                                    .testTag("save_google_account_system")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT,
                                        onClick = {
                                            saveDestination = ContactSaveDestination.GOOGLE_ACCOUNT
                                            selectedGoogleEmail = ""
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Google Account (via Google Contacts)",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Saves in VoxLine and opens Google Contacts to sync",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            TextButton(
                                onClick = onOpenAddGoogleAccount,
                                modifier = Modifier.testTag("add_google_account_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PersonAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Sign in to Google Account on Phone",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (saveDestination == ContactSaveDestination.LOCAL_VAULT) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { saveDestination = ContactSaveDestination.LOCAL_VAULT }
                                .testTag("save_local_vault_option")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = saveDestination == ContactSaveDestination.LOCAL_VAULT,
                                    onClick = { saveDestination = ContactSaveDestination.LOCAL_VAULT }
                                )
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Device Vault Only",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Encrypted on this phone without Google sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Pin to Favorites Toggle Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { favorite = !favorite }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (favorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "Pin to Favorites & Speed Dial",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Show at the top of Favorites and Keypad quick strip",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = favorite,
                            onCheckedChange = { favorite = it },
                            modifier = Modifier.testTag("contact_favorite_switch")
                        )
                    }
                }

                // Bottom Primary Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("cancel_contact_button")
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = submitContact,
                        enabled = canSave,
                        modifier = Modifier
                            .weight(1.6f)
                            .height(52.dp)
                            .testTag("save_contact_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (saveDestination == ContactSaveDestination.GOOGLE_ACCOUNT) {
                                "Save to Google"
                            } else {
                                "Save Contact"
                            }
                        )
                    }
                }
            }
        }
    }
}
