package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ContactEntity
import com.example.telecom.IncomingCallNotificationManager
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.BlockedNumbersScreen
import com.example.ui.screens.ContactEditorScreen
import com.example.ui.screens.ContactSaveDraft
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.ControlTab
import com.example.ui.screens.DialerScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.RecentsScreen
import com.example.ui.theme.VoxLineTheme
import com.example.viewmodel.VoxLineViewModel

private data class ContactEditorRequest(
    val contact: ContactEntity? = null,
    val prefillPhone: String = "",
    val prefillName: String = ""
)

private enum class VoxDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
) {
    FAVORITES("Favorites", Icons.Filled.Star, Icons.Outlined.StarBorder, "nav_favorites"),
    RECENTS("Recents", Icons.Filled.History, Icons.Outlined.History, "nav_recents"),
    CONTACTS("Contacts", Icons.Filled.Contacts, Icons.Outlined.Contacts, "nav_contacts")
}

class MainActivity : ComponentActivity() {

    private val viewModel: VoxLineViewModel by viewModels()
    private val openKeypadOnDialIntent = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        IncomingCallNotificationManager.ensureChannels(this)
        if (intent?.getBooleanExtra(IncomingCallNotificationManager.EXTRA_OPEN_ACTIVE_CALL, false) == true) {
            applyCallOverDisplayWindowFlags(enable = true)
        }
        viewModel.handleIncomingIntent(intent)
        if (isDialIntent(intent)) {
            openKeypadOnDialIntent.value = true
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()

            LaunchedEffect(settings.screenPrivacySecure) {
                if (settings.screenPrivacySecure) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            LaunchedEffect(activeCall != null) {
                applyCallOverDisplayWindowFlags(enable = activeCall != null)
            }

            VoxLineTheme(
                themeMode = settings.themeMode,
                accentPalette = settings.accentPalette,
                highContrast = settings.highContrast,
                fontScaleMultiplier = settings.fontSizeOption.scale
            ) {
                VoxLineRootApp(
                    viewModel = viewModel,
                    openKeypadFromIntent = openKeypadOnDialIntent.value,
                    onConsumedIntentKeypad = { openKeypadOnDialIntent.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(IncomingCallNotificationManager.EXTRA_OPEN_ACTIVE_CALL, false)) {
            applyCallOverDisplayWindowFlags(enable = true)
        }
        viewModel.handleIncomingIntent(intent)
        if (isDialIntent(intent)) {
            openKeypadOnDialIntent.value = true
        }
    }

    private fun applyCallOverDisplayWindowFlags(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(enable)
            setTurnScreenOn(enable)
        } else {
            @Suppress("DEPRECATION")
            val legacyFlags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            if (enable) {
                window.addFlags(legacyFlags)
            } else {
                window.clearFlags(legacyFlags)
            }
        }
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun isDialIntent(intent: Intent?): Boolean {
        val scheme = intent?.data?.scheme?.lowercase()
        return scheme == "tel"
    }
}

@Composable
private fun VoxLineRootApp(
    viewModel: VoxLineViewModel,
    openKeypadFromIntent: Boolean,
    onConsumedIntentKeypad: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val forwardingRules by viewModel.forwardingRules.collectAsStateWithLifecycle()
    val spamRules by viewModel.spamRules.collectAsStateWithLifecycle()
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val activeSims by viewModel.activeSims.collectAsStateWithLifecycle()
    val isDefaultDialer by viewModel.isDefaultDialer.collectAsStateWithLifecycle()
    val hasPhoneStatePermission by viewModel.hasPhoneStatePermission.collectAsStateWithLifecycle()
    val hasContactsPermission by viewModel.hasContactsPermission.collectAsStateWithLifecycle()
    val hasWriteContactsPermission by viewModel.hasWriteContactsPermission.collectAsStateWithLifecycle()
    val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle()
    val hasCallLogPermission by viewModel.hasCallLogPermission.collectAsStateWithLifecycle()
    val isSyncingContacts by viewModel.isSyncingContacts.collectAsStateWithLifecycle()
    val isSyncingCallLogs by viewModel.isSyncingCallLogs.collectAsStateWithLifecycle()
    val lastContactsAutoSyncAt by viewModel.lastContactsAutoSyncAt.collectAsStateWithLifecycle()
    val lastCallLogsAutoSyncAt by viewModel.lastCallLogsAutoSyncAt.collectAsStateWithLifecycle()
    val dialInput by viewModel.dialInput.collectAsStateWithLifecycle()
    val selectedSimIndex by viewModel.selectedSimIndex.collectAsStateWithLifecycle()
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val playingRecordingId by viewModel.playingRecordingId.collectAsStateWithLifecycle()
    val availableRingtones by viewModel.availableRingtones.collectAsStateWithLifecycle()
    val previewingRingtoneUri by viewModel.previewingRingtoneUri.collectAsStateWithLifecycle()
    val liveProbe by viewModel.liveQualityProbe.collectAsStateWithLifecycle()
    val isProbingQuality by viewModel.isProbingQuality.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // App opens directly to Recents Call screen
    var currentDestination by rememberSaveable { mutableStateOf(VoxDestination.RECENTS) }
    var currentControlTab by rememberSaveable { mutableStateOf(ControlTab.RECORDING) }
    var showKeypadPage by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBlockedNumbersScreen by rememberSaveable { mutableStateOf(false) }
    var contactEditorRequest by remember { mutableStateOf<ContactEditorRequest?>(null) }
    var pendingSyncPassphrase by remember { mutableStateOf("") }
    var pendingContactDraft by remember { mutableStateOf<ContactSaveDraft?>(null) }

    LaunchedEffect(openKeypadFromIntent) {
        if (openKeypadFromIntent) {
            showKeypadPage = true
            onConsumedIntentKeypad()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var canShowIncomingOverDisplay by remember {
        mutableStateOf(
            IncomingCallNotificationManager.hasNotificationPermission(context) &&
                IncomingCallNotificationManager.canUseFullScreenIntent(context)
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceTelephonyState()
                canShowIncomingOverDisplay =
                    IncomingCallNotificationManager.hasNotificationPermission(context) &&
                        IncomingCallNotificationManager.canUseFullScreenIntent(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val postNotificationsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        canShowIncomingOverDisplay = granted && IncomingCallNotificationManager.canUseFullScreenIntent(context)
        if (granted && !IncomingCallNotificationManager.canUseFullScreenIntent(context)) {
            runCatching {
                context.startActivity(
                    IncomingCallNotificationManager.createFullScreenIntentSettingsIntent(context)
                )
            }
        }
    }

    val requestIncomingOverDisplayAction: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !IncomingCallNotificationManager.hasNotificationPermission(context)
        ) {
            postNotificationsPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    IncomingCallNotificationManager.createFullScreenIntentSettingsIntent(context)
                )
            }
        }
    }

    // Default Dialer role launcher
    val defaultDialerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshDeviceTelephonyState()
    }

    // Runtime permission launchers (least-privilege, requested on demand)
    val autoRecordAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setAutoRecord(granted)
    }

    val readContactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.importDeviceContacts()
    }

    val writeContactsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val draft = pendingContactDraft
        pendingContactDraft = null
        if (draft != null) {
            viewModel.saveContact(draft)
        }
    }

    val handleSaveContactWithGooglePerm: (ContactSaveDraft) -> Unit = { draft ->
        if (draft.saveToGoogleAccount &&
            draft.googleAccountName.isNotBlank() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingContactDraft = draft
            writeContactsPermLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        } else {
            viewModel.saveContact(draft)
        }
    }

    val readCallLogPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.syncSystemCallLogs()
    }

    val autoSyncPermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.onDeviceSyncPermissionsUpdated()
    }

    val phonePermsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshDeviceTelephonyState()
    }

    val requestDefaultDialerAction: () -> Unit = {
        val roleIntent = viewModel.telecomBridge.createDefaultDialerIntent()
        if (roleIntent != null) {
            defaultDialerLauncher.launch(roleIntent)
        } else {
            phonePermsLauncher.launch(
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.READ_PHONE_STATE
                )
            )
        }
    }

    // Automatically sync & import Contacts and Recents on app launch (requesting missing permissions once)
    var requestedInitialAutoSyncPerms by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val missingPerms = buildList {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CONTACTS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.READ_CONTACTS)
            }
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_CALL_LOG
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.READ_CALL_LOG)
            }
        }
        if (missingPerms.isEmpty()) {
            viewModel.autoSyncDeviceData(silent = true)
        } else if (!requestedInitialAutoSyncPerms) {
            requestedInitialAutoSyncPerms = true
            autoSyncPermsLauncher.launch(missingPerms.toTypedArray())
        }
    }

    // Zero-permission SAF Export / Import for AES-256-GCM encrypted sync bundles
    val exportSyncLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && pendingSyncPassphrase.length >= 6) {
            viewModel.exportEncryptedSync(uri, pendingSyncPassphrase)
        }
    }

    val importSyncLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && pendingSyncPassphrase.length >= 6) {
            viewModel.importEncryptedSync(uri, pendingSyncPassphrase)
        }
    }

    // Zero-permission System Ringtone Picker for SIM calls
    val systemRingtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            viewModel.applyPickedRingtoneUri(pickedUri = pickedUri, isCustomAudio = false)
        }
    }

    // Zero-permission Custom Audio File Picker (SAF OpenDocument) for SIM ringtone
    val customAudioRingtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.applyPickedRingtoneUri(pickedUri = uri, isCustomAudio = true)
        }
    }

    val openSystemRingtonePicker: () -> Unit = {
        val pickerIntent = viewModel.ringtoneEngine.createSystemRingtonePickerIntent(
            currentUri = settings.ringtones.simRingtoneUri,
            title = "Select Incoming Call Ringtone"
        )
        runCatching { systemRingtonePickerLauncher.launch(pickerIntent) }
    }

    val openCustomAudioPicker: () -> Unit = {
        runCatching { customAudioRingtoneLauncher.launch(arrayOf("audio/*")) }
    }

    val openSettingsAtTab: (ControlTab) -> Unit = { tab ->
        showKeypadPage = false
        contactEditorRequest = null
        currentControlTab = tab
        showSettings = true
    }

    // Handle Back navigation cleanly from secondary tabs back to Recents
    BackHandler(
        enabled = activeCall == null &&
            contactEditorRequest == null &&
            !showBlockedNumbersScreen &&
            !showSettings &&
            !showKeypadPage &&
            currentDestination != VoxDestination.RECENTS
    ) {
        currentDestination = VoxDestination.RECENTS
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 700.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    if (!showKeypadPage && !showSettings && !showBlockedNumbersScreen && contactEditorRequest == null && activeCall == null) {
                        FloatingActionButton(
                            onClick = { showKeypadPage = true },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("open_keypad_popup_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Dialpad,
                                contentDescription = "Open Keypad"
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar {
                            VoxDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail {
                            VoxDestination.entries.forEach { dest ->
                                val selected = currentDestination == dest
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        when (currentDestination) {
                            VoxDestination.FAVORITES -> FavoritesScreen(
                                contacts = contacts,
                                callLogs = callLogs,
                                recordings = recordings,
                                playingRecordingId = playingRecordingId,
                                onCallContact = { addr ->
                                    viewModel.startCall(addr)
                                },
                                onToggleFavorite = viewModel::toggleFavoriteContact,
                                onTogglePlayback = viewModel::togglePlayback,
                                onShareRecording = viewModel::shareRecording,
                                onDeleteLog = viewModel::deleteCallLog,
                                onOpenSettings = { openSettingsAtTab(currentControlTab) }
                            )

                            VoxDestination.RECENTS -> RecentsScreen(
                                callLogs = callLogs,
                                contacts = contacts,
                                isDefaultDialer = isDefaultDialer,
                                canShowIncomingOverDisplay = canShowIncomingOverDisplay,
                                recordings = recordings,
                                playingRecordingId = playingRecordingId,
                                hasCallLogPermission = hasCallLogPermission,
                                isSyncingCallLogs = isSyncingCallLogs,
                                lastAutoSyncTimestamp = lastCallLogsAutoSyncAt,
                                onCallBack = { number ->
                                    viewModel.startCall(number)
                                },
                                onOpenAddContact = { phoneNumber, prefillName ->
                                    contactEditorRequest = ContactEditorRequest(
                                        contact = null,
                                        prefillPhone = phoneNumber,
                                        prefillName = prefillName
                                    )
                                },
                                onRequestDefaultDialer = requestDefaultDialerAction,
                                onEnableIncomingOverDisplay = requestIncomingOverDisplayAction,
                                onTogglePlayback = viewModel::togglePlayback,
                                onShareRecording = viewModel::shareRecording,
                                onDeleteRecording = viewModel::deleteRecording,
                                onDeleteLog = viewModel::deleteCallLog,
                                onSaveCallNote = viewModel::saveCallLogNote,
                                onOpenRecordingSettings = {
                                    openSettingsAtTab(ControlTab.RECORDING)
                                },
                                onOpenSettings = { openSettingsAtTab(currentControlTab) }
                            )

                            VoxDestination.CONTACTS -> ContactsScreen(
                                contacts = contacts,
                                callLogs = callLogs,
                                recordings = recordings,
                                playingRecordingId = playingRecordingId,
                                hasContactsPermission = hasContactsPermission,
                                isSyncingContacts = isSyncingContacts,
                                lastAutoSyncTimestamp = lastContactsAutoSyncAt,
                                decryptNote = viewModel.repository::decryptNote,
                                onOpenAddContact = {
                                    contactEditorRequest = ContactEditorRequest()
                                },
                                onOpenEditContact = { existing ->
                                    contactEditorRequest = ContactEditorRequest(contact = existing)
                                },
                                onToggleFavorite = viewModel::toggleFavoriteContact,
                                onDeleteContact = viewModel::deleteContact,
                                onCallContact = { addr ->
                                    viewModel.startCall(addr)
                                },
                                onTogglePlayback = viewModel::togglePlayback,
                                onShareRecording = viewModel::shareRecording,
                                onDeleteLog = viewModel::deleteCallLog,
                                onImportDeviceContacts = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.READ_CONTACTS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.importDeviceContacts()
                                    } else {
                                        readContactsPermLauncher.launch(Manifest.permission.READ_CONTACTS)
                                    }
                                },
                                onOpenSettings = { openSettingsAtTab(currentControlTab) }
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Keypad Dial Page
        AnimatedVisibility(
            visible = showKeypadPage && !showSettings && contactEditorRequest == null && activeCall == null,
            enter = slideInVertically(initialOffsetY = { it / 5 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            DialerScreen(
                dialInput = dialInput,
                selectedSimIndex = selectedSimIndex,
                activeSims = activeSims,
                contacts = contacts,
                callLogs = callLogs,
                isDefaultDialer = isDefaultDialer,
                hasPhoneStatePermission = hasPhoneStatePermission,
                themeMode = settings.themeMode,
                keypadLayout = settings.keypadLayout,
                dialerSize = settings.dialerSizeOption,
                autoRecordEnabled = settings.autoRecordCalls,
                autoRecordMode = settings.autoRecordMode,
                autoRecordSpecificCount = settings.autoRecordNumbers.size,
                onDigitClick = viewModel::appendDigit,
                onPlayDialTone = viewModel::playDialTone,
                onSetDialInput = viewModel::setDialInput,
                onBackspace = viewModel::backspaceDialInput,
                onClear = viewModel::clearDialInput,
                onSelectSimIndex = viewModel::selectSimSlot,
                onSelectKeypadLayout = viewModel::setKeypadLayout,
                onSelectDialerSize = viewModel::setDialerSizeOption,
                onToggleLightDarkTheme = viewModel::toggleLightDarkTheme,
                onRequestDefaultDialer = requestDefaultDialerAction,
                onRequestPhonePermissions = {
                    phonePermsLauncher.launch(
                        arrayOf(
                            Manifest.permission.CALL_PHONE,
                            Manifest.permission.READ_PHONE_STATE
                        )
                    )
                },
                onOpenPhoneSimSettings = viewModel::openPhoneSimSettings,
                onPlaceCall = { addr, preferredSim ->
                    if (addr.isNotBlank()) {
                        showKeypadPage = false
                    }
                    viewModel.startCall(address = addr, preferredSim = preferredSim)
                },
                onAddNewContact = { number ->
                    contactEditorRequest = ContactEditorRequest(
                        contact = null,
                        prefillPhone = number
                    )
                },
                onOpenSettings = {
                    openSettingsAtTab(currentControlTab)
                },
                onOpenRecordingSettings = {
                    openSettingsAtTab(ControlTab.RECORDING)
                },
                onNavigateToFavorites = {
                    showKeypadPage = false
                    currentDestination = VoxDestination.FAVORITES
                },
                onNavigateToRecents = {
                    showKeypadPage = false
                    currentDestination = VoxDestination.RECENTS
                },
                onNavigateToContacts = {
                    showKeypadPage = false
                    currentDestination = VoxDestination.CONTACTS
                },
                onBack = { showKeypadPage = false }
            )
        }

        // Full-Screen Add New Contact / Edit Contact Page
        var lastContactRequest by remember { mutableStateOf<ContactEditorRequest?>(null) }
        val activeContactRequest = contactEditorRequest
        if (activeContactRequest != null) {
            lastContactRequest = activeContactRequest
        }
        AnimatedVisibility(
            visible = activeContactRequest != null && activeCall == null,
            enter = slideInVertically(initialOffsetY = { it / 5 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 5 }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            lastContactRequest?.let { req ->
                ContactEditorScreen(
                    initial = req.contact,
                    initialNote = req.contact?.let { viewModel.repository.decryptNote(it.encryptedNotes) }.orEmpty(),
                    prefillPhone = req.prefillPhone,
                    prefillName = req.prefillName,
                    googleAccounts = googleAccounts,
                    hasWriteContactsPermission = hasWriteContactsPermission,
                    onOpenAddGoogleAccount = viewModel::openAddGoogleAccountSettings,
                    onBack = { contactEditorRequest = null },
                    onSave = { draft ->
                        handleSaveContactWithGooglePerm(draft)
                        contactEditorRequest = null
                    }
                )
            }
        }

        // Settings Screen containing all Control options (Recording, Ringtones, Quality, Forwarding, Spam, Privacy & Theme)
        if (showSettings && !showBlockedNumbersScreen && contactEditorRequest == null && activeCall == null) {
            ControlCenterScreen(
                selectedTab = currentControlTab,
                onSelectTab = { currentControlTab = it },
                onBack = {
                    viewModel.stopRingtonePreview()
                    showBlockedNumbersScreen = false
                    showSettings = false
                },
                settings = settings,
                vaultStatus = viewModel.vaultStatus,
                recordingStorageFolder = viewModel.recordingStorageFolder,
                contacts = contacts,
                recordings = recordings,
                playingRecordingId = playingRecordingId,
                availableRingtones = availableRingtones,
                previewingRingtoneUri = previewingRingtoneUri,
                callLogs = callLogs,
                forwardingRules = forwardingRules,
                spamRules = spamRules,
                liveProbe = liveProbe,
                isProbingQuality = isProbingQuality,
                onToggleAutoRecord = { enable ->
                    if (enable) {
                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasAudioPerm) {
                            viewModel.setAutoRecord(true)
                        } else {
                            autoRecordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        viewModel.setAutoRecord(false)
                    }
                },
                onSelectAutoRecordMode = viewModel::setAutoRecordMode,
                onAddAutoRecordNumber = viewModel::addAutoRecordNumber,
                onRemoveAutoRecordNumber = viewModel::removeAutoRecordNumber,
                onClearAutoRecordNumbers = viewModel::clearAutoRecordNumbers,
                onToggleRecordingPlayback = viewModel::togglePlayback,
                onShareRecording = viewModel::shareRecording,
                onDeleteRecording = viewModel::deleteRecording,
                onSelectRingtone = { uri, title ->
                    viewModel.selectSimRingtone(uri, title, playPreview = true)
                },
                onToggleRingtonePreview = viewModel::toggleRingtonePreview,
                onStopRingtonePreview = viewModel::stopRingtonePreview,
                onOpenSystemRingtonePicker = openSystemRingtonePicker,
                onOpenCustomAudioRingtonePicker = openCustomAudioPicker,
                onSetVibrateSim = viewModel::setVibrateOnSimRing,
                onResetRingtones = viewModel::resetRingtonesToDefault,
                onRunLiveProbe = viewModel::runLiveQualityProbe,
                onToggleCallForwarding = viewModel::toggleCallForwarding,
                onSaveCallForwarding = viewModel::saveCallForwardingPreferences,
                onApplyForwardingRule = viewModel::saveAndApplyForwardingRule,
                onCancelAllCarrierForwarding = viewModel::cancelAllCarrierForwarding,
                onAddQuickReplyTemplate = viewModel::addQuickReplyTemplate,
                onRemoveQuickReplyTemplate = viewModel::removeQuickReplyTemplate,
                onResetQuickReplyTemplates = viewModel::resetQuickReplyTemplates,
                onOpenBlockedNumbers = { showBlockedNumbersScreen = true },
                onSetThemeMode = viewModel::setThemeMode,
                onSetAccentPalette = viewModel::setAccentPalette,
                onSetHighContrast = viewModel::setHighContrast,
                onSetFontSizeOption = viewModel::setFontSizeOption,
                onSetDialerSizeOption = viewModel::setDialerSizeOption,
                onSetKeypadLayout = viewModel::setKeypadLayout,
                onSetScreenPrivacy = viewModel::setScreenPrivacy,
                onSetDtmfTones = viewModel::setDtmfTones,
                onRequestExportSync = { pass ->
                    pendingSyncPassphrase = pass
                    exportSyncLauncher.launch("voxline_encrypted_sync.vxync")
                },
                onRequestImportSync = { pass ->
                    pendingSyncPassphrase = pass
                    importSyncLauncher.launch(arrayOf("*/*"))
                },
                onWipeVault = viewModel::wipeEncryptedVault
            )
        }

        // Dedicated Full-Screen Blocked Numbers Settings Page
        AnimatedVisibility(
            visible = showBlockedNumbersScreen && activeCall == null,
            enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            BlockedNumbersScreen(
                blockedRules = remember(spamRules) { spamRules.filter { it.autoBlock } },
                contacts = contacts,
                callLogs = callLogs,
                onBlockNumber = viewModel::blockPhoneNumber,
                onUnblockNumber = viewModel::unblockPhoneNumber,
                onClearAllBlocked = viewModel::clearAllBlockedNumbers,
                onBack = { showBlockedNumbersScreen = false },
                snackbarHostState = snackbarHostState
            )
        }

        // Full-Screen Active Call & Real-Time Quality Telemetry HUD Overlay
        activeCall?.let { session ->
            ActiveCallOverlay(
                session = session,
                contacts = contacts,
                quickReplyTemplates = settings.quickReplyTemplates,
                onToggleMute = viewModel::toggleMute,
                onToggleSpeaker = viewModel::toggleSpeaker,
                onToggleHold = viewModel::toggleHold,
                onToggleRecord = viewModel::toggleCallRecording,
                onRetryRecord = viewModel::retryCallRecording,
                onRecordPermissionDenied = viewModel::onRecordAudioPermissionDenied,
                onSendDtmf = viewModel::appendDigit,
                onAddCallToConference = viewModel::addCallToConference,
                onMergeCalls = viewModel::mergeCallsIntoConference,
                onSwapCalls = viewModel::swapActiveAndHeldCalls,
                onSeparateParticipant = viewModel::splitConferenceParticipant,
                onDisconnectParticipant = viewModel::disconnectConferenceParticipant,
                onAnswerCall = viewModel::answerIncomingCall,
                onDeclineCall = viewModel::declineIncomingCall,
                onDeclineWithQuickReply = viewModel::declineWithQuickReply,
                onEndCall = viewModel::endActiveCall
            )
        }
    }
}
