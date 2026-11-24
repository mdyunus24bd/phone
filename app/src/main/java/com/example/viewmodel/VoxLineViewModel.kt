package com.example.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CallContactInfo
import com.example.data.local.CallDirection
import com.example.data.local.CallForwardingRuleEntity
import com.example.data.local.CallLogEntity
import com.example.data.local.CallRecordingEntity
import com.example.data.local.CallType
import com.example.data.local.CallTypeSummary
import com.example.data.local.ContactEntity
import com.example.data.local.SpamRuleEntity
import com.example.data.local.VoxLineDatabase
import com.example.data.preferences.AppFontSizeOption
import com.example.data.preferences.AutoRecordMode
import com.example.data.preferences.CallForwardingCondition
import com.example.data.preferences.DialerSizeOption
import com.example.data.preferences.KeypadLayoutStyle
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.preferences.VoxLineSettings
import com.example.data.repository.GoogleContactAccount
import com.example.data.repository.VoxLineRepository
import com.example.security.CryptoVault
import com.example.ui.screens.ContactSaveDraft
import com.example.telecom.ActiveTelecomCallBus
import com.example.telecom.CallRecordingManager
import com.example.telecom.CallRecordingState
import com.example.telecom.ConferenceParticipantSnapshot
import com.example.telecom.IncomingCallRinger
import com.example.telecom.NetworkQualityEngine
import com.example.telecom.NetworkQualitySample
import com.example.telecom.RingtoneEngine
import com.example.telecom.RingtoneOption
import com.example.telecom.SecondaryCallSnapshot
import com.example.telecom.SimSlotInfo
import com.example.telecom.SpamAnalyzer
import com.example.telecom.SpamAssessment
import com.example.telecom.SpamRiskLevel
import com.example.telecom.TelecomBridge
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ActiveCallSession(
    val address: String,
    val displayName: String,
    val networkType: String = "SIM",
    val routeLabel: String,
    val isSystemTelecomCall: Boolean,
    val isRinging: Boolean = false,
    val isIncoming: Boolean = false,
    val statusLabel: String = "Connecting...",
    val elapsedSec: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,
    val isRecording: Boolean = false,
    val isAutoRecording: Boolean = false,
    val isRecordingBusy: Boolean = false,
    val recordingAudioSource: String = "",
    val recordingSec: Long = 0L,
    val recordingAudioLevel: Float = 0f,
    val isRecordingSilenced: Boolean = false,
    val encryptedTransport: Boolean = false,
    val qualitySamples: List<NetworkQualitySample> = emptyList(),
    val spamAssessment: SpamAssessment? = null,
    val finishedRecordingId: Long? = null,
    val cannedQuickReplies: List<String> = emptyList(),
    val photoUri: String = "",
    val isConference: Boolean = false,
    val conferenceParticipants: List<ConferenceParticipantSnapshot> = emptyList(),
    val secondaryCall: SecondaryCallSnapshot? = null,
    val canMergeCalls: Boolean = false,
    val canSwapCalls: Boolean = false
)

class VoxLineViewModel(application: Application) : AndroidViewModel(application) {

    private val cryptoVault = CryptoVault()
    private val prefsRepo = UserPreferencesRepository(application)
    private val db = VoxLineDatabase.getInstance(application)
    val repository = VoxLineRepository(application, db, cryptoVault, prefsRepo)

    val telecomBridge = TelecomBridge(application)
    val ringtoneEngine = RingtoneEngine(application)
    private val networkQualityEngine = NetworkQualityEngine(application)
    val callRecordingManager = CallRecordingManager(application, cryptoVault)

    val recordingState: StateFlow<CallRecordingState> = callRecordingManager.recordingState
    val vaultStatus: CryptoVault.VaultStatus = cryptoVault.getStatus()
    val recordingStorageFolder: String = callRecordingManager.internalStorageFolderLabel()

    val settings: StateFlow<VoxLineSettings> = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        VoxLineSettings()
    )

    val contacts: StateFlow<List<ContactEntity>> = repository.contactsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val callLogs: StateFlow<List<CallLogEntity>> = repository.callLogsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val callTypeSummaries: StateFlow<List<CallTypeSummary>> =
        repository.callTypeSummariesFlow.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            emptyList()
        )

    val forwardingRules: StateFlow<List<CallForwardingRuleEntity>> = repository.forwardingRulesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val spamRules: StateFlow<List<SpamRuleEntity>> = repository.spamRulesFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    val recordings: StateFlow<List<CallRecordingEntity>> = repository.recordingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000L),
        emptyList()
    )

    private val _isDefaultDialer = MutableStateFlow(telecomBridge.isDefaultDialer())
    val isDefaultDialer: StateFlow<Boolean> = _isDefaultDialer.asStateFlow()

    private val _hasPhoneStatePermission = MutableStateFlow(telecomBridge.hasPhoneStatePermission())
    val hasPhoneStatePermission: StateFlow<Boolean> = _hasPhoneStatePermission.asStateFlow()

    private val _hasContactsPermission = MutableStateFlow(checkPermission(Manifest.permission.READ_CONTACTS))
    val hasContactsPermission: StateFlow<Boolean> = _hasContactsPermission.asStateFlow()

    private val _hasWriteContactsPermission = MutableStateFlow(checkPermission(Manifest.permission.WRITE_CONTACTS))
    val hasWriteContactsPermission: StateFlow<Boolean> = _hasWriteContactsPermission.asStateFlow()

    private val _googleAccounts = MutableStateFlow<List<GoogleContactAccount>>(emptyList())
    val googleAccounts: StateFlow<List<GoogleContactAccount>> = _googleAccounts.asStateFlow()

    private val _hasCallLogPermission = MutableStateFlow(checkPermission(Manifest.permission.READ_CALL_LOG))
    val hasCallLogPermission: StateFlow<Boolean> = _hasCallLogPermission.asStateFlow()

    private val _isSyncingContacts = MutableStateFlow(false)
    val isSyncingContacts: StateFlow<Boolean> = _isSyncingContacts.asStateFlow()

    private val _isSyncingCallLogs = MutableStateFlow(false)
    val isSyncingCallLogs: StateFlow<Boolean> = _isSyncingCallLogs.asStateFlow()

    private val _lastContactsAutoSyncAt = MutableStateFlow(0L)
    val lastContactsAutoSyncAt: StateFlow<Long> = _lastContactsAutoSyncAt.asStateFlow()

    private val _lastCallLogsAutoSyncAt = MutableStateFlow(0L)
    val lastCallLogsAutoSyncAt: StateFlow<Long> = _lastCallLogsAutoSyncAt.asStateFlow()

    private val _activeSims = MutableStateFlow(telecomBridge.detectActiveSims())
    val activeSims: StateFlow<List<SimSlotInfo>> = _activeSims.asStateFlow()

    private val _dialInput = MutableStateFlow("")
    val dialInput: StateFlow<String> = _dialInput.asStateFlow()

    private val _selectedSimIndex = MutableStateFlow(TelecomBridge.DEFAULT_SIM_SLOT_INDEX)
    val selectedSimIndex: StateFlow<Int> = _selectedSimIndex.asStateFlow()

    private val _activeCall = MutableStateFlow<ActiveCallSession?>(null)
    val activeCall: StateFlow<ActiveCallSession?> = _activeCall.asStateFlow()

    private val _playingRecordingId = MutableStateFlow<Long?>(null)
    val playingRecordingId: StateFlow<Long?> = _playingRecordingId.asStateFlow()

    private val _availableRingtones = MutableStateFlow<List<RingtoneOption>>(ringtoneEngine.loadAvailableRingtones())
    val availableRingtones: StateFlow<List<RingtoneOption>> = _availableRingtones.asStateFlow()

    private val _previewingRingtoneUri = MutableStateFlow<String?>(null)
    val previewingRingtoneUri: StateFlow<String?> = _previewingRingtoneUri.asStateFlow()

    private val _liveQualityProbe = MutableStateFlow<NetworkQualitySample?>(null)
    val liveQualityProbe: StateFlow<NetworkQualitySample?> = _liveQualityProbe.asStateFlow()

    private val _isProbingQuality = MutableStateFlow(false)
    val isProbingQuality: StateFlow<Boolean> = _isProbingQuality.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var callTimerJob: Job? = null
    private var telemetrySamplerJob: Job? = null
    private var contactsObserverDebounceJob: Job? = null
    private var callLogObserverDebounceJob: Job? = null
    private val contactsSyncMutex = Mutex()
    private val callLogSyncMutex = Mutex()
    private var contactsObserverRegistered = false
    private var callLogObserverRegistered = false
    private var subscriptionsListenerRegistered = false

    private val subscriptionsChangedListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            _activeSims.value = telecomBridge.detectActiveSims()
        }
    }

    private val contactsContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            contactsObserverDebounceJob?.cancel()
            contactsObserverDebounceJob = viewModelScope.launch {
                delay(650L)
                importDeviceContacts(silent = true)
            }
        }
    }

    private val callLogContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            callLogObserverDebounceJob?.cancel()
            callLogObserverDebounceJob = viewModelScope.launch {
                delay(650L)
                syncSystemCallLogs(silent = true)
            }
        }
    }

    init {
        viewModelScope.launch {
            repository.ensureDefaultForwardingTemplates()
        }
        ensureSubscriptionsListenerRegistered()
        autoSyncDeviceData(silent = true)
        viewModelScope.launch {
            callRecordingManager.liveAudioLevel.collect { level ->
                if (_activeCall.value?.isRecording == true) {
                    _activeCall.update { it?.copy(recordingAudioLevel = level) }
                }
            }
        }
        viewModelScope.launch {
            callRecordingManager.isAudioSilenced.collect { silenced ->
                if (_activeCall.value?.isRecording == true) {
                    _activeCall.update { it?.copy(isRecordingSilenced = silenced) }
                }
            }
        }
        viewModelScope.launch {
            ActiveTelecomCallBus.systemCall.collect { sysCall ->
                if (sysCall != null) {
                    val existing = _activeCall.value
                    val resolvedParticipants = sysCall.conferenceParticipants.map { participant ->
                        val pDigits = participant.handle.filter { it.isDigit() }
                        val pContact = contacts.value.firstOrNull { c ->
                            val cd = c.phoneNumber.filter { it.isDigit() }
                            cd.isNotEmpty() && pDigits.isNotEmpty() && (
                                cd == pDigits ||
                                    (cd.length >= 10 && pDigits.length >= 10 && cd.takeLast(10) == pDigits.takeLast(10))
                                )
                        }
                        if (pContact != null) {
                            participant.copy(displayName = pContact.displayName)
                        } else {
                            participant
                        }
                    }
                    val resolvedSecondary = sysCall.secondaryCall?.let { sec ->
                        if (sec.isConference) {
                            sec
                        } else {
                            val sDigits = sec.handle.filter { it.isDigit() }
                            val sContact = contacts.value.firstOrNull { c ->
                                val cd = c.phoneNumber.filter { it.isDigit() }
                                cd.isNotEmpty() && sDigits.isNotEmpty() && (
                                    cd == sDigits ||
                                        (cd.length >= 10 && sDigits.length >= 10 && cd.takeLast(10) == sDigits.takeLast(10))
                                    )
                            }
                            if (sContact != null) sec.copy(displayName = sContact.displayName) else sec
                        }
                    }

                    val liveStatus = when {
                        sysCall.isConference && sysCall.isHolding -> "Conference On Hold"
                        sysCall.isConference -> "Conference • ${resolvedParticipants.size.coerceAtLeast(2)} callers"
                        sysCall.isRinging -> "Incoming Call"
                        sysCall.isHolding -> "Call On Hold"
                        sysCall.isDialing -> "Dialing..."
                        sysCall.isConnected -> "Connected"
                        else -> "Calling"
                    }

                    val matched = if (sysCall.isConference) null else repository.findContactByAddress(sysCall.handle)
                    val resolvedDisplayName = when {
                        sysCall.isConference -> "Conference Call"
                        else -> matched?.displayName.orEmpty()
                    }

                    val resolvedSimRoute = telecomBridge.resolveSimLabel(
                        accountId = sysCall.phoneAccountId,
                        accountLabel = sysCall.phoneAccountLabel,
                        fallbackSlotIndex = if (sysCall.isIncoming) {
                            TelecomBridge.DEFAULT_SIM_SLOT_INDEX
                        } else {
                            _selectedSimIndex.value
                        }
                    )

                    if (existing == null || !existing.isSystemTelecomCall) {
                        _activeCall.value = ActiveCallSession(
                            address = sysCall.handle,
                            displayName = resolvedDisplayName,
                            networkType = "SIM",
                            routeLabel = resolvedSimRoute,
                            isSystemTelecomCall = true,
                            isRinging = sysCall.isRinging,
                            isIncoming = sysCall.isIncoming,
                            statusLabel = liveStatus,
                            isOnHold = sysCall.isHolding,
                            encryptedTransport = false,
                            spamAssessment = null,
                            cannedQuickReplies = sysCall.cannedTextResponses,
                            photoUri = matched?.photoUri.orEmpty(),
                            isConference = sysCall.isConference,
                            conferenceParticipants = resolvedParticipants,
                            secondaryCall = resolvedSecondary,
                            canMergeCalls = sysCall.canMergeCalls,
                            canSwapCalls = sysCall.canSwapCalls
                        )
                        // Stop any Settings ringtone preview; VoxLineInCallService owns IncomingCallRinger
                        stopRingtonePreview()
                        if (!sysCall.isRinging) {
                            IncomingCallRinger.stop()
                            if (sysCall.isConnected) {
                                tryStartAutoRecordingIfEligible(sysCall.handle)
                                startCallTelemetryLoops()
                            }
                        }
                    } else {
                        val justConnected = !sysCall.isRinging && sysCall.isConnected && existing.isRinging
                        val primaryChanged = existing.address != sysCall.handle || existing.isConference != sysCall.isConference
                        _activeCall.update { cur ->
                            cur?.copy(
                                address = sysCall.handle,
                                displayName = if (primaryChanged) resolvedDisplayName else cur.displayName,
                                routeLabel = if (sysCall.phoneAccountId.isNotBlank() || sysCall.phoneAccountLabel.isNotBlank()) {
                                    resolvedSimRoute
                                } else {
                                    cur.routeLabel
                                },
                                photoUri = if (primaryChanged) matched?.photoUri.orEmpty() else cur.photoUri,
                                isRinging = sysCall.isRinging,
                                isOnHold = sysCall.isHolding,
                                statusLabel = liveStatus,
                                cannedQuickReplies = sysCall.cannedTextResponses,
                                isConference = sysCall.isConference,
                                conferenceParticipants = resolvedParticipants,
                                secondaryCall = resolvedSecondary,
                                canMergeCalls = sysCall.canMergeCalls,
                                canSwapCalls = sysCall.canSwapCalls
                            )
                        }
                        if (!sysCall.isRinging) {
                            stopRingtonePreview()
                            IncomingCallRinger.stop()
                        }
                        if (justConnected || (sysCall.isConnected && callTimerJob == null)) {
                            tryStartAutoRecordingIfEligible(sysCall.handle)
                            startCallTelemetryLoops()
                        }
                    }
                } else if (_activeCall.value?.isSystemTelecomCall == true) {
                    stopRingtonePreview()
                    IncomingCallRinger.stop()
                    finalizeActiveCall()
                }
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureSystemContentObserversRegistered() {
        val resolver = getApplication<Application>().contentResolver
        if (_hasContactsPermission.value && !contactsObserverRegistered) {
            runCatching {
                resolver.registerContentObserver(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    true,
                    contactsContentObserver
                )
                contactsObserverRegistered = true
            }
        }
        if (_hasCallLogPermission.value && !callLogObserverRegistered) {
            runCatching {
                resolver.registerContentObserver(
                    CallLog.Calls.CONTENT_URI,
                    true,
                    callLogContentObserver
                )
                callLogObserverRegistered = true
            }
        }
    }

    /**
     * Automatically imports and keeps device Contacts and Recents Call Logs synced
     * whenever permissions are granted (on launch, ON_RESUME, and via live ContentObservers).
     */
    fun autoSyncDeviceData(silent: Boolean = true) {
        val contactsGranted = checkPermission(Manifest.permission.READ_CONTACTS)
        val writeContactsGranted = checkPermission(Manifest.permission.WRITE_CONTACTS)
        val callLogGranted = checkPermission(Manifest.permission.READ_CALL_LOG)
        _hasContactsPermission.value = contactsGranted
        _hasWriteContactsPermission.value = writeContactsGranted
        _hasCallLogPermission.value = callLogGranted
        ensureSystemContentObserversRegistered()

        if (!contactsGranted && !callLogGranted) return

        viewModelScope.launch {
            var importedContacts = 0
            var importedLogs = 0
            if (contactsGranted) {
                _googleAccounts.value = repository.detectGoogleAccounts()
                importedContacts = runContactsImportInternal(silent = true)
            }
            if (callLogGranted) {
                importedLogs = runCallLogSyncInternal(silent = true)
            }
            if (!silent || importedContacts > 0 || importedLogs > 0) {
                val parts = buildList {
                    if (importedContacts > 0) add("$importedContacts contacts")
                    if (importedLogs > 0) add("$importedLogs recent calls")
                }
                if (parts.isNotEmpty()) {
                    showMessage("Auto-synced ${parts.joinToString(" & ")} from device.")
                }
            }
        }
    }

    fun onDeviceSyncPermissionsUpdated() {
        autoSyncDeviceData(silent = false)
    }

    private fun ensureSubscriptionsListenerRegistered() {
        if (subscriptionsListenerRegistered) return
        if (!telecomBridge.hasPhoneStatePermission() && !telecomBridge.isDefaultDialer()) return
        val app = getApplication<Application>()
        val subManager = app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                subManager.addOnSubscriptionsChangedListener(
                    ContextCompat.getMainExecutor(app),
                    subscriptionsChangedListener
                )
            } else {
                @Suppress("DEPRECATION")
                subManager.addOnSubscriptionsChangedListener(subscriptionsChangedListener)
            }
            subscriptionsListenerRegistered = true
        }
    }

    fun refreshDeviceTelephonyState() {
        _isDefaultDialer.value = telecomBridge.isDefaultDialer()
        _hasPhoneStatePermission.value = telecomBridge.hasPhoneStatePermission()
        _hasWriteContactsPermission.value = checkPermission(Manifest.permission.WRITE_CONTACTS)
        ensureSubscriptionsListenerRegistered()
        _activeSims.value = telecomBridge.detectActiveSims()
        autoSyncDeviceData(silent = true)
    }

    fun openAddGoogleAccountSettings() {
        val opened = repository.openAddGoogleAccountSettings()
        if (!opened) {
            showMessage("Open Android Settings → Accounts to sign in to your Google Account.")
        }
    }

    fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme?.lowercase() == "tel") {
            _dialInput.value = data.schemeSpecificPart.orEmpty()
        }
    }

    fun setDialInput(value: String) {
        _dialInput.value = value
    }

    fun appendDigit(digit: Char) {
        _dialInput.update { it + digit }
        playDialTone(digit)
    }

    fun playDialTone(digit: Char) {
        if (settings.value.dtmfTonesEnabled) {
            telecomBridge.playDtmfTone(digit)
        }
        if (_activeCall.value?.isSystemTelecomCall == true) {
            ActiveTelecomCallBus.playDtmf(digit)
        }
    }

    fun backspaceDialInput() {
        _dialInput.update { if (it.isNotEmpty()) it.dropLast(1) else "" }
    }

    fun clearDialInput() {
        _dialInput.value = ""
    }

    fun selectSimSlot(index: Int) {
        _selectedSimIndex.value = index
    }

    fun openPhoneSimSettings() {
        val opened = telecomBridge.openPhoneSimSettings()
        if (!opened) {
            showMessage("Open Android Settings → Network & Internet → SIMs to choose your default calling SIM.")
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    /**
     * Initiates a real carrier SIM call via Android Telecom.
     * No simulated/demo call sessions are created when a carrier SIM is unavailable.
     */
    fun startCall(
        address: String = _dialInput.value,
        preferredSim: SimSlotInfo? = null
    ) {
        val cleanAddress = address.trim()
        if (cleanAddress.isEmpty()) {
            showMessage("Enter a phone number first.")
            return
        }

        viewModelScope.launch {
            // Evaluate DataStore Call Forwarding preference or Room Always-Forward rule
            val dsForward = settings.value.callForwarding
            val dsForwardMatch = dsForward.enabled &&
                dsForward.condition == CallForwardingCondition.ALWAYS &&
                dsForward.forwardingNumber.isNotBlank()

            val alwaysForward = forwardingRules.value.firstOrNull {
                it.conditionKey == "ALWAYS" && it.enabled && it.targetDestination.isNotBlank()
            }
            val activeForwardDestination = when {
                dsForwardMatch -> dsForward.forwardingNumber.trim()
                alwaysForward != null -> alwaysForward.targetDestination
                else -> null
            }
            val finalDestination = activeForwardDestination ?: cleanAddress

            // Real Carrier SIM Call Dispatch via Android Telecom / ACTION_DIAL
            val dispatched = telecomBridge.placeCarrierSimCall(
                number = finalDestination,
                preferredSlotIndex = preferredSim?.slotIndex ?: _selectedSimIndex.value,
                preferredSim = preferredSim
            )
            if (!dispatched) {
                showMessage("Unable to place call on this device.")
            } else if (!telecomBridge.isDefaultDialer()) {
                showMessage("Dialing $finalDestination...")
            }
        }
    }

    /**
     * Checks whether the current call address qualifies for Automatic Call Recording
     * under either All Calls or Specific Numbers mode.
     */
    fun shouldAutoRecordAddress(address: String): Boolean {
        val currentSettings = settings.value
        if (!currentSettings.autoRecordCalls) return false
        return when (currentSettings.autoRecordMode) {
            AutoRecordMode.ALL_CALLS -> true
            AutoRecordMode.SPECIFIC_NUMBERS -> {
                val targetTrimmed = address.trim()
                val targetDigits = targetTrimmed.filter { it.isDigit() }
                currentSettings.autoRecordNumbers.any { rule ->
                    val ruleTrimmed = rule.trim()
                    if (ruleTrimmed.equals(targetTrimmed, ignoreCase = true)) {
                        true
                    } else {
                        val ruleDigits = ruleTrimmed.filter { it.isDigit() }
                        if (ruleDigits.isNotEmpty() && targetDigits.isNotEmpty()) {
                            ruleDigits == targetDigits ||
                                (ruleDigits.length >= 7 && targetDigits.endsWith(ruleDigits)) ||
                                (targetDigits.length >= 7 && ruleDigits.endsWith(targetDigits))
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }

    private fun tryStartAutoRecordingIfEligible(address: String) {
        if (!shouldAutoRecordAddress(address)) return
        if (!callRecordingManager.hasRecordAudioPermission()) {
            showMessage("Auto-recording skipped: grant Microphone permission in Recording settings.")
            return
        }
        val currentSession = _activeCall.value ?: return
        viewModelScope.launch {
            _activeCall.update { it?.copy(isRecordingBusy = true) }
            val started = callRecordingManager.startBackgroundRecording(
                callerLabel = currentSession.displayName.ifBlank { currentSession.address },
                networkType = currentSession.networkType,
                isAutoTriggered = true
            )
            if (started.isSuccess) {
                val sourceLabel = started.getOrDefault("VOICE_RECOG")
                _activeCall.update {
                    it?.copy(
                        isRecording = true,
                        isAutoRecording = true,
                        isRecordingBusy = false,
                        recordingAudioSource = sourceLabel,
                        recordingSec = 0L,
                        isRecordingSilenced = callRecordingManager.isAudioSilenced.value
                    )
                }
                val modeLabel = if (settings.value.autoRecordMode == AutoRecordMode.SPECIFIC_NUMBERS) {
                    "Specific Number rule"
                } else {
                    "Auto-Record"
                }
                showMessage("$modeLabel active • Recording in background to App Internal Storage.")
            } else {
                _activeCall.update { it?.copy(isRecordingBusy = false) }
                showMessage("Auto-record unavailable: ${started.exceptionOrNull()?.localizedMessage ?: "Audio busy"}")
            }
        }
    }

    private fun startCallTelemetryLoops() {
        callTimerJob?.cancel()
        telemetrySamplerJob?.cancel()

        callTimerJob = viewModelScope.launch {
            while (isActive && _activeCall.value != null) {
                delay(1000L)
                _activeCall.update { session ->
                    session?.copy(
                        elapsedSec = if (session.isOnHold) session.elapsedSec else session.elapsedSec + 1L,
                        recordingSec = if (session.isRecording) session.recordingSec + 1L else session.recordingSec
                    )
                }
            }
        }

        telemetrySamplerJob = viewModelScope.launch {
            while (isActive && _activeCall.value != null) {
                val sample = networkQualityEngine.sampleLiveNetworkQuality()
                _activeCall.update { session ->
                    session?.copy(
                        qualitySamples = (session.qualitySamples + sample).takeLast(24)
                    )
                }
                delay(2800L)
            }
        }
    }

    fun answerIncomingCall() {
        val current = _activeCall.value ?: return
        stopRingtonePreview()
        IncomingCallRinger.stop()
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.answer()
        }
    }

    fun declineIncomingCall() {
        val current = _activeCall.value ?: return
        stopRingtonePreview()
        IncomingCallRinger.stop()
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.reject()
        } else {
            finalizeActiveCall()
        }
    }

    /**
     * Declines an incoming call and sends a predefined or custom Quick Reply SMS message.
     * Uses Android Telecom's native Call.reject(true, message) Respond-Via-SMS API when
     * supported, or rejects the call and opens the zero-permission smsto: SMS composer.
     */
    fun declineWithQuickReply(message: String) {
        val current = _activeCall.value ?: return
        val cleanMsg = message.trim()
        if (cleanMsg.isEmpty()) {
            showMessage("Enter or choose a quick reply message first.")
            return
        }
        stopRingtonePreview()
        IncomingCallRinger.stop()
        if (current.isSystemTelecomCall) {
            val handledNatively = ActiveTelecomCallBus.rejectWithMessage(cleanMsg)
            if (handledNatively) {
                showMessage("Declined call & sent Quick Reply SMS.")
            } else {
                val openedSms = telecomBridge.openQuickReplySms(current.address, cleanMsg)
                showMessage(
                    if (openedSms) "Call declined • Quick Reply ready in Messages."
                    else "Call declined • Unable to open SMS app."
                )
            }
        } else {
            val targetNumber = current.address
            finalizeActiveCall()
            val openedSms = telecomBridge.openQuickReplySms(targetNumber, cleanMsg)
            showMessage(
                if (openedSms) "Call declined • Quick Reply ready in Messages."
                else "Call declined."
            )
        }
    }

    fun addQuickReplyTemplate(message: String) = viewModelScope.launch {
        val cleaned = message.trim()
        if (cleaned.isEmpty()) {
            showMessage("Quick reply message cannot be empty.")
            return@launch
        }
        prefsRepo.addQuickReplyTemplate(cleaned)
        showMessage("Added Quick Reply SMS template.")
    }

    fun removeQuickReplyTemplate(message: String) = viewModelScope.launch {
        prefsRepo.removeQuickReplyTemplate(message)
        showMessage("Removed Quick Reply template.")
    }

    fun resetQuickReplyTemplates() = viewModelScope.launch {
        prefsRepo.resetQuickReplyTemplates()
        showMessage("Restored default Quick Reply SMS messages.")
    }

    fun toggleMute() {
        val current = _activeCall.value ?: return
        val nextMuted = !current.isMuted
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.setMuted(nextMuted)
        } else {
            val audio = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching { audio?.isMicrophoneMute = nextMuted }
        }
        _activeCall.update { it?.copy(isMuted = nextMuted) }
    }

    fun toggleSpeaker() {
        val current = _activeCall.value ?: return
        val nextSpeaker = !current.isSpeakerOn
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.setSpeaker(nextSpeaker)
        } else {
            val audio = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            @Suppress("DEPRECATION")
            runCatching { audio?.isSpeakerphoneOn = nextSpeaker }
        }
        _activeCall.update { it?.copy(isSpeakerOn = nextSpeaker) }
    }

    fun toggleHold() {
        val current = _activeCall.value ?: return
        val nextHold = !current.isOnHold
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.hold(nextHold)
        }
        _activeCall.update {
            it?.copy(
                isOnHold = nextHold,
                statusLabel = if (nextHold) "Call On Hold" else "Active Call"
            )
        }
    }

    // Carrier Conference Call Controls
    fun addCallToConference(phoneNumber: String) {
        val clean = phoneNumber.trim()
        if (clean.isEmpty() || clean.none { it.isDigit() }) {
            showMessage("Enter a valid phone number to add to the call.")
            return
        }
        val dispatched = telecomBridge.placeCarrierSimCall(
            number = clean,
            preferredSlotIndex = _selectedSimIndex.value
        )
        if (dispatched) {
            showMessage("Dialing $clean — tap Merge once connected to start conference.")
        } else {
            showMessage("Unable to add another SIM call on this device.")
        }
    }

    fun mergeCallsIntoConference() {
        val merged = ActiveTelecomCallBus.mergeCalls()
        if (merged) {
            showMessage("Merging calls into carrier conference...")
        } else {
            showMessage("Add and connect a second call first, or check carrier conference support.")
        }
    }

    fun swapActiveAndHeldCalls() {
        val swapped = ActiveTelecomCallBus.swapCalls()
        if (!swapped) {
            showMessage("No held call available to swap.")
        }
    }

    fun splitConferenceParticipant(callId: String) {
        val ok = ActiveTelecomCallBus.separateConferenceParticipant(callId)
        if (ok) {
            showMessage("Separated caller into a private call.")
        } else {
            showMessage("Carrier does not support splitting this participant.")
        }
    }

    fun disconnectConferenceParticipant(callId: String) {
        val ok = ActiveTelecomCallBus.disconnectConferenceParticipant(callId)
        if (ok) {
            showMessage("Removed participant from conference call.")
        }
    }

    fun onRecordAudioPermissionDenied(permanentlyDenied: Boolean) {
        showMessage(
            if (permanentlyDenied) {
                "Microphone permission is permanently denied. Enable it in App Settings to record calls."
            } else {
                "Microphone permission is required to record call audio."
            }
        )
    }

    fun toggleCallRecording(hasAudioPermission: Boolean) {
        val current = _activeCall.value ?: return
        if (current.isRecordingBusy) return

        if (!current.isRecording) {
            if (!hasAudioPermission || !callRecordingManager.hasRecordAudioPermission()) {
                showMessage("Microphone permission is needed to record audio to App Internal Storage.")
                return
            }
            viewModelScope.launch {
                _activeCall.update { it?.copy(isRecordingBusy = true) }
                val result = callRecordingManager.startBackgroundRecording(
                    callerLabel = current.displayName.ifBlank { current.address },
                    networkType = current.networkType,
                    isAutoTriggered = false
                )
                if (result.isSuccess) {
                    val sourceLabel = result.getOrDefault("VOICE_RECOG")
                    _activeCall.update {
                        it?.copy(
                            isRecording = true,
                            isAutoRecording = false,
                            isRecordingBusy = false,
                            recordingAudioSource = sourceLabel,
                            recordingSec = 0L,
                            isRecordingSilenced = callRecordingManager.isAudioSilenced.value
                        )
                    }
                    showMessage("Call recording started • Saving to App Internal Storage.")
                } else {
                    _activeCall.update { it?.copy(isRecordingBusy = false) }
                    showMessage("Recording unavailable: ${result.exceptionOrNull()?.localizedMessage ?: "Audio busy"}")
                }
            }
        } else {
            viewModelScope.launch {
                _activeCall.update { it?.copy(isRecordingBusy = true) }
                val finished = callRecordingManager.stopAndSaveRecording(
                    phoneNumber = current.address,
                    networkType = current.networkType
                )
                if (finished.isSuccess) {
                    val rec = finished.getOrThrow()
                    val recId = repository.insertRecording(
                        CallRecordingEntity(
                            phoneNumber = current.address,
                            displayName = current.displayName,
                            networkType = current.networkType,
                            fileName = rec.fileName,
                            storageUri = rec.storageUri,
                            storagePath = rec.storagePath,
                            autoRecorded = current.isAutoRecording,
                            durationSec = rec.durationSec,
                            fileSizeBytes = rec.fileSizeBytes,
                            encryptedAtRest = rec.encryptedAtRest
                        )
                    )
                    _activeCall.update {
                        it?.copy(
                            isRecording = false,
                            isAutoRecording = false,
                            isRecordingBusy = false,
                            recordingAudioSource = "",
                            recordingAudioLevel = 0f,
                            isRecordingSilenced = false,
                            finishedRecordingId = recId
                        )
                    }
                    showMessage("Saved to App Internal Storage (${rec.durationSec}s): ${rec.fileName}")
                } else {
                    _activeCall.update {
                        it?.copy(
                            isRecording = false,
                            isAutoRecording = false,
                            isRecordingBusy = false,
                            recordingAudioSource = "",
                            recordingAudioLevel = 0f,
                            isRecordingSilenced = false
                        )
                    }
                    showMessage("Could not save recording: ${finished.exceptionOrNull()?.message}")
                }
            }
        }
    }

    /**
     * Re-probes Android audio sources during an active call (e.g., after enabling
     * VoxLine Call Audio Helper or switching speakerphone) to capture non-silenced audio.
     */
    fun retryCallRecording() {
        val current = _activeCall.value ?: return
        if (current.isRecordingBusy || !callRecordingManager.hasRecordAudioPermission()) return
        viewModelScope.launch {
            _activeCall.update { it?.copy(isRecordingBusy = true) }
            if (current.isRecording || callRecordingManager.isRecording) {
                callRecordingManager.cancelRecording()
            }
            val result = callRecordingManager.startBackgroundRecording(
                callerLabel = current.displayName.ifBlank { current.address },
                networkType = current.networkType,
                isAutoTriggered = current.isAutoRecording
            )
            if (result.isSuccess) {
                val sourceLabel = result.getOrDefault("VOICE_RECOG")
                _activeCall.update {
                    it?.copy(
                        isRecording = true,
                        isRecordingBusy = false,
                        recordingAudioSource = sourceLabel,
                        recordingSec = 0L,
                        isRecordingSilenced = callRecordingManager.isAudioSilenced.value
                    )
                }
                showMessage("Re-started call capture on $sourceLabel.")
            } else {
                _activeCall.update {
                    it?.copy(
                        isRecording = false,
                        isRecordingBusy = false,
                        recordingAudioLevel = 0f,
                        isRecordingSilenced = false
                    )
                }
                showMessage("Recording unavailable: ${result.exceptionOrNull()?.localizedMessage ?: "Audio busy"}")
            }
        }
    }

    fun endActiveCall() {
        val current = _activeCall.value ?: return
        if (current.isSystemTelecomCall) {
            ActiveTelecomCallBus.disconnect()
        }
        finalizeActiveCall()
    }

    private fun finalizeActiveCall() {
        val session = _activeCall.value ?: return
        callTimerJob?.cancel()
        telemetrySamplerJob?.cancel()

        viewModelScope.launch {
            var recordingId = session.finishedRecordingId
            if (session.isRecording || callRecordingManager.isRecording) {
                val finished = callRecordingManager.stopAndSaveRecording(
                    phoneNumber = session.address,
                    networkType = session.networkType
                )
                if (finished.isSuccess) {
                    val rec = finished.getOrThrow()
                    recordingId = repository.insertRecording(
                        CallRecordingEntity(
                            phoneNumber = session.address,
                            displayName = session.displayName,
                            networkType = session.networkType,
                            fileName = rec.fileName,
                            storageUri = rec.storageUri,
                            storagePath = rec.storagePath,
                            autoRecorded = session.isAutoRecording,
                            durationSec = rec.durationSec,
                            fileSizeBytes = rec.fileSizeBytes,
                            encryptedAtRest = rec.encryptedAtRest
                        )
                    )
                    showMessage("Recording saved to App Internal Storage (${rec.durationSec}s).")
                }
            }

            callTimerJob = null
            telemetrySamplerJob = null
            val samples = session.qualitySamples
            val avgMos = if (samples.isNotEmpty()) samples.map { it.mosScore }.average().toFloat() else 0f
            val avgLat = if (samples.isNotEmpty()) samples.map { it.latencyMs }.average().toInt() else 0
            val avgJit = if (samples.isNotEmpty()) samples.map { it.jitterMs }.average().toInt() else 0
            val avgLoss = if (samples.isNotEmpty()) samples.map { it.packetLossPct }.average().toFloat() else 0f
            val codec = samples.lastOrNull()?.codecLabel ?: "AMR-WB HD"

            val matchedContact = repository.findContactByAddress(session.address)
            val resolvedDirection = when {
                session.isIncoming && session.elapsedSec == 0L -> CallDirection.MISSED
                session.isIncoming -> CallDirection.INCOMING
                else -> CallDirection.OUTGOING
            }
            repository.insertCallLog(
                CallLogEntity(
                    phoneNumber = session.address,
                    displayName = matchedContact?.displayName ?: session.displayName,
                    callType = CallType.SIM,
                    routeLabel = session.routeLabel,
                    direction = resolvedDirection,
                    timestamp = System.currentTimeMillis(),
                    durationSec = session.elapsedSec,
                    contactInfo = CallContactInfo(
                        contactId = matchedContact?.id,
                        name = matchedContact?.displayName ?: session.displayName,
                        phoneNumber = matchedContact?.phoneNumber.orEmpty().ifBlank { session.address },
                        label = matchedContact?.label.orEmpty()
                    ),
                    mosScore = avgMos,
                    avgLatencyMs = avgLat,
                    avgJitterMs = avgJit,
                    packetLossPct = avgLoss,
                    codec = codec,
                    encryptedTransport = session.encryptedTransport,
                    spamScore = session.spamAssessment?.score ?: 0,
                    spamReason = session.spamAssessment?.reason.orEmpty(),
                    recordingId = recordingId,
                    deviceTag = settings.value.deviceSyncName
                )
            )
            _activeCall.value = null
        }
    }

    // Phone Storage Recording Playback
    fun togglePlayback(recording: CallRecordingEntity) {
        if (_playingRecordingId.value == recording.id) {
            callRecordingManager.stopPlayback()
            _playingRecordingId.value = null
            return
        }
        viewModelScope.launch {
            _playingRecordingId.value = recording.id
            val result = callRecordingManager.playRecording(
                fileName = recording.fileName,
                storageUri = recording.storageUri,
                encryptedAtRest = recording.encryptedAtRest
            ) {
                _playingRecordingId.value = null
            }
            if (result.isFailure) {
                _playingRecordingId.value = null
                showMessage("Playback failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun deleteRecording(recording: CallRecordingEntity) {
        viewModelScope.launch {
            if (_playingRecordingId.value == recording.id) {
                callRecordingManager.stopPlayback()
                _playingRecordingId.value = null
            }
            callRecordingManager.deleteRecordingFile(recording.fileName, recording.storageUri)
            repository.deleteRecording(recording)
            showMessage("Recording deleted from App Internal Storage.")
        }
    }

    fun shareRecording(recording: CallRecordingEntity) {
        viewModelScope.launch {
            val prepared = callRecordingManager.prepareShareableRecording(
                fileName = recording.fileName,
                storageUri = recording.storageUri,
                encryptedAtRest = recording.encryptedAtRest
            )
            val (uri, mimeType) = prepared.getOrElse { err ->
                showMessage("Unable to share recording: ${err.message ?: "File unavailable"}")
                return@launch
            }
            val label = recording.displayName.ifBlank { recording.phoneNumber }.ifBlank { "Call Recording" }
            val launched = callRecordingManager.launchShareRecordingIntent(
                uri = uri,
                mimeType = mimeType,
                subject = "Call recording - $label"
            )
            if (!launched) {
                showMessage("No app available to share this recording.")
            }
        }
    }

    // Contacts CRUD + Photo Persistence + Google Account Sync
    fun saveContact(draft: ContactSaveDraft) {
        if (draft.displayName.isBlank()) {
            showMessage("Contact name cannot be empty.")
            return
        }
        viewModelScope.launch {
            _hasWriteContactsPermission.value = checkPermission(Manifest.permission.WRITE_CONTACTS)
            val persistedPhotoUri = if (draft.photoUri.isNotBlank()) {
                repository.persistContactPhoto(draft.photoUri)
            } else {
                ""
            }

            var syncedGoogleAccount = ""
            var deviceContactId = 0L
            var statusMessage: String? = null

            if (draft.saveToGoogleAccount) {
                val targetAccount = draft.googleAccountName.ifBlank {
                    _googleAccounts.value.firstOrNull()?.accountName.orEmpty()
                }
                val googleResult = repository.saveContactToGoogleAccount(
                    displayName = draft.displayName,
                    phoneNumber = draft.phoneNumber,
                    favorite = draft.favorite,
                    photoUri = persistedPhotoUri,
                    googleAccountName = targetAccount
                )
                if (googleResult.savedToGoogleAccount) {
                    syncedGoogleAccount = googleResult.accountName
                    deviceContactId = googleResult.deviceContactId
                } else if (!googleResult.openedSystemFallback && !_hasWriteContactsPermission.value) {
                    // Fallback to zero-permission Android Google Contacts screen when WRITE_CONTACTS is denied
                    repository.openSystemGoogleContactsInsert(
                        displayName = draft.displayName,
                        phoneNumber = draft.phoneNumber
                    )
                }
                statusMessage = googleResult.message
            }

            repository.upsertContact(
                id = draft.id,
                displayName = draft.displayName,
                phoneNumber = draft.phoneNumber,
                label = draft.label.ifBlank { if (syncedGoogleAccount.isNotBlank()) "Google" else "Mobile" },
                favorite = draft.favorite,
                plainNotes = draft.notes,
                avatarColorIndex = kotlin.math.abs(draft.displayName.hashCode()) % 5,
                photoUri = persistedPhotoUri,
                googleAccountName = syncedGoogleAccount,
                deviceContactId = deviceContactId
            )

            showMessage(
                statusMessage ?: if (draft.id == 0L) {
                    "Contact saved to encrypted vault."
                } else {
                    "Contact updated."
                }
            )
        }
    }

    fun toggleFavoriteContact(contact: ContactEntity) {
        viewModelScope.launch { repository.toggleFavorite(contact) }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch {
            repository.deleteContact(contact)
            showMessage("Removed ${contact.displayName}.")
        }
    }

    private suspend fun runContactsImportInternal(silent: Boolean): Int {
        return contactsSyncMutex.withLock {
            _isSyncingContacts.value = true
            try {
                val res = repository.importDeviceContacts()
                if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    _lastContactsAutoSyncAt.value = System.currentTimeMillis()
                    if (!silent) {
                        showMessage(
                            if (count > 0) "Imported $count device contacts."
                            else "All device contacts are already synced."
                        )
                    }
                    count
                } else {
                    if (!silent) {
                        showMessage(res.exceptionOrNull()?.message ?: "Unable to import contacts.")
                    }
                    0
                }
            } finally {
                _isSyncingContacts.value = false
            }
        }
    }

    private suspend fun runCallLogSyncInternal(silent: Boolean): Int {
        return callLogSyncMutex.withLock {
            _isSyncingCallLogs.value = true
            try {
                val res = repository.syncSystemCallLogs(settings.value.deviceSyncName)
                if (res.isSuccess) {
                    val count = res.getOrDefault(0)
                    _lastCallLogsAutoSyncAt.value = System.currentTimeMillis()
                    if (!silent) {
                        showMessage(
                            if (count > 0) "Synced $count recent calls from device."
                            else "Recents call log is up to date."
                        )
                    }
                    count
                } else {
                    if (!silent) {
                        showMessage(res.exceptionOrNull()?.message ?: "Unable to read system call log.")
                    }
                    0
                }
            } finally {
                _isSyncingCallLogs.value = false
            }
        }
    }

    fun importDeviceContacts(silent: Boolean = false) {
        _hasContactsPermission.value = checkPermission(Manifest.permission.READ_CONTACTS)
        ensureSystemContentObserversRegistered()
        viewModelScope.launch {
            runContactsImportInternal(silent = silent)
        }
    }

    fun syncSystemCallLogs(silent: Boolean = false) {
        _hasCallLogPermission.value = checkPermission(Manifest.permission.READ_CALL_LOG)
        ensureSystemContentObserversRegistered()
        viewModelScope.launch {
            runCallLogSyncInternal(silent = silent)
        }
    }

    fun deleteCallLog(id: Long) {
        viewModelScope.launch { repository.deleteCallLog(id) }
    }

    fun saveCallLogNote(callLogId: Long, note: String) {
        val cleaned = note.trim().take(240)
        viewModelScope.launch {
            repository.updateCallLogNote(callLogId, cleaned)
            showMessage(
                if (cleaned.isNotEmpty()) "Call note saved."
                else "Call note cleared."
            )
        }
    }

    // DataStore-Backed Call Forwarding Settings
    fun toggleCallForwarding(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setCallForwardingEnabled(enabled)
            val currentNum = settings.value.callForwarding.forwardingNumber
            showMessage(
                if (enabled && currentNum.isNotBlank()) "Call forwarding enabled → $currentNum"
                else if (enabled) "Call forwarding enabled. Specify a forwarding number."
                else "Call forwarding disabled."
            )
        }
    }

    fun saveCallForwardingPreferences(
        enabled: Boolean,
        forwardingNumber: String,
        condition: CallForwardingCondition,
        dispatchCarrierMmi: Boolean
    ) {
        val cleanNumber = forwardingNumber.trim()
        if (enabled && cleanNumber.isBlank()) {
            showMessage("Please enter a forwarding number.")
            return
        }
        viewModelScope.launch {
            prefsRepo.saveCallForwardingSettings(
                enabled = enabled,
                forwardingNumber = cleanNumber,
                condition = condition
            )
            if (dispatchCarrierMmi) {
                val code = if (enabled && cleanNumber.isNotBlank()) {
                    "${condition.mmiPrefix}$cleanNumber#"
                } else {
                    condition.deactivateCode
                }
                val ok = telecomBridge.dispatchForwardingMmiCode(code)
                showMessage(
                    if (ok) "Saved to DataStore & dispatched MMI: $code"
                    else "Call forwarding saved to DataStore ($code)."
                )
            } else {
                showMessage(
                    if (enabled) "Call forwarding saved to DataStore → $cleanNumber"
                    else "Call forwarding preference saved (disabled)."
                )
            }
        }
    }

    // Call Forwarding Rules + Carrier MMI Dispatch
    fun saveAndApplyForwardingRule(
        rule: CallForwardingRuleEntity,
        destination: String,
        enabled: Boolean,
        dispatchCarrierMmi: Boolean
    ) {
        viewModelScope.launch {
            val updated = rule.copy(
                targetDestination = destination.trim(),
                networkType = "SIM",
                enabled = enabled
            )
            repository.updateForwardingRule(updated)
            if (rule.conditionKey == "ALWAYS") {
                prefsRepo.saveCallForwardingSettings(
                    enabled = enabled,
                    forwardingNumber = destination.trim(),
                    condition = CallForwardingCondition.ALWAYS
                )
            }
            if (dispatchCarrierMmi) {
                val code = if (enabled && destination.isNotBlank()) {
                    "${rule.activatePrefix}${destination.trim()}#"
                } else {
                    rule.deactivateCode
                }
                val ok = telecomBridge.dispatchForwardingMmiCode(code)
                showMessage(if (ok) "Dispatched carrier MMI code: $code" else "Saved rule locally ($code)")
            } else {
                showMessage(
                    if (enabled) "${rule.title} active for SIM → ${destination.trim()}"
                    else "${rule.title} disabled."
                )
            }
        }
    }

    fun cancelAllCarrierForwarding() {
        viewModelScope.launch {
            repository.disableAllForwardingRules()
            prefsRepo.setCallForwardingEnabled(false)
            telecomBridge.dispatchForwardingMmiCode("##002#")
            showMessage("Disabled all rules & dispatched ##002# carrier reset.")
        }
    }

    // Spam Shield & Blocked Numbers
    fun addSpamRule(pattern: String, label: String, riskScore: Int = 95, autoBlock: Boolean = true) {
        if (pattern.isBlank()) {
            showMessage("Enter a phone number or prefix (e.g. +1900*).")
            return
        }
        viewModelScope.launch {
            repository.addSpamRule(pattern, label, riskScore, autoBlock)
            showMessage("Added '$pattern' to Spam Shield.")
        }
    }

    fun deleteSpamRule(rule: SpamRuleEntity) {
        viewModelScope.launch {
            repository.deleteSpamRule(rule)
            showMessage("Removed rule ${rule.pattern}.")
        }
    }

    fun blockPhoneNumber(phoneNumber: String, label: String = "") {
        val clean = phoneNumber.trim()
        if (clean.isEmpty() || clean.none { it.isDigit() }) {
            showMessage("Enter a valid phone number to block.")
            return
        }
        viewModelScope.launch {
            val added = repository.blockNumber(clean, label)
            if (added) {
                showMessage("Blocked $clean — incoming calls will be auto-rejected.")
            } else {
                showMessage("$clean is already in your Blocked Numbers list.")
            }
        }
    }

    fun unblockPhoneNumber(rule: SpamRuleEntity) {
        viewModelScope.launch {
            repository.deleteSpamRule(rule)
            showMessage("Unblocked ${rule.pattern}.")
        }
    }

    fun clearAllBlockedNumbers() {
        viewModelScope.launch {
            repository.clearAllBlockedNumbers()
            showMessage("Cleared all blocked numbers.")
        }
    }

    fun evaluateManualSpamCheck(query: String): SpamAssessment {
        val trimmed = query.trim()
        val queryDigits = trimmed.filter { it.isDigit() }
        val known = contacts.value.any {
            it.phoneNumber.equals(trimmed, ignoreCase = true) ||
                (queryDigits.isNotEmpty() && it.phoneNumber.filter { ch -> ch.isDigit() } == queryDigits)
        }
        return SpamAnalyzer.evaluate(query, known, spamRules.value)
    }

    // Live SIM Network Quality Probe
    fun runLiveQualityProbe() {
        viewModelScope.launch {
            _isProbingQuality.value = true
            val sample = networkQualityEngine.sampleLiveNetworkQuality()
            _liveQualityProbe.value = sample
            _isProbingQuality.value = false
        }
    }

    // Encrypted Cross-Device Sync Export / Import
    fun exportEncryptedSync(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val res = repository.exportEncryptedSyncBundle(uri, passphrase, settings.value.deviceSyncName)
            if (res.isSuccess) {
                val s = res.getOrThrow()
                showMessage(
                    "Encrypted sync exported (${s.callLogsCount} calls, ${s.contactsCount} contacts • SHA ${s.checksumShort})"
                )
            } else {
                showMessage("Export failed: ${res.exceptionOrNull()?.message}")
            }
        }
    }

    fun importEncryptedSync(uri: Uri, passphrase: String) {
        viewModelScope.launch {
            val res = repository.importEncryptedSyncBundle(uri, passphrase)
            if (res.isSuccess) {
                val s = res.getOrThrow()
                showMessage(
                    "Synced ${s.callLogsCount} calls, ${s.contactsCount} contacts."
                )
            } else {
                showMessage("Sync decrypt failed: check passphrase or file.")
            }
        }
    }

    // Theme, Keypad & Recording Settings
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    fun toggleLightDarkTheme() = viewModelScope.launch {
        val next = if (settings.value.themeMode == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        prefsRepo.setThemeMode(next)
        showMessage("Switched to ${next.label} theme.")
    }
    fun setAccentPalette(palette: AccentPalette) = viewModelScope.launch { prefsRepo.setAccentPalette(palette) }
    fun setHighContrast(enabled: Boolean) = viewModelScope.launch { prefsRepo.setHighContrast(enabled) }
    fun setFontSizeOption(option: AppFontSizeOption) = viewModelScope.launch {
        prefsRepo.setFontSizeOption(option)
        showMessage("Font size: ${option.label} (${option.percentLabel})")
    }
    fun setDialerSizeOption(option: DialerSizeOption) = viewModelScope.launch {
        prefsRepo.setDialerSizeOption(option)
        showMessage("Dialer size: ${option.label}")
    }
    fun setAutoRecord(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAutoRecordCalls(enabled)
        showMessage(
            if (enabled) "Automatic call recording enabled (saves to App Internal Storage)."
            else "Automatic call recording disabled."
        )
    }
    fun setAutoRecordMode(mode: AutoRecordMode) = viewModelScope.launch {
        prefsRepo.setAutoRecordMode(mode)
        showMessage("Auto-record mode: ${mode.label}")
    }
    fun addAutoRecordNumber(number: String) = viewModelScope.launch {
        val cleaned = number.trim()
        if (cleaned.isEmpty()) {
            showMessage("Enter a phone number first.")
            return@launch
        }
        prefsRepo.addAutoRecordNumber(cleaned)
        showMessage("Added $cleaned to auto-record list.")
    }
    fun removeAutoRecordNumber(number: String) = viewModelScope.launch {
        prefsRepo.removeAutoRecordNumber(number)
        showMessage("Removed $number from auto-record list.")
    }
    fun clearAutoRecordNumbers() = viewModelScope.launch {
        prefsRepo.clearAutoRecordNumbers()
        showMessage("Cleared specific auto-record number list.")
    }
    fun setKeypadLayout(layout: KeypadLayoutStyle) = viewModelScope.launch {
        prefsRepo.setKeypadLayout(layout)
        showMessage("Keypad layout: ${layout.label}")
    }
    fun cycleKeypadLayout() = viewModelScope.launch {
        val entries = KeypadLayoutStyle.entries
        val nextIndex = (entries.indexOf(settings.value.keypadLayout) + 1) % entries.size
        val next = entries[nextIndex]
        prefsRepo.setKeypadLayout(next)
        showMessage("Keypad layout: ${next.label}")
    }
    // SIM Ringtone Management
    fun selectSimRingtone(uri: String, title: String, playPreview: Boolean = true) {
        viewModelScope.launch {
            prefsRepo.setSimRingtone(uri, title)
            showMessage("Incoming call ringtone set to $title")
            if (playPreview) {
                startRingtonePreview(
                    uri = uri,
                    vibrate = settings.value.ringtones.vibrateOnSimRing
                )
            }
        }
    }

    fun applyPickedRingtoneUri(pickedUri: Uri?, isCustomAudio: Boolean = false) {
        viewModelScope.launch {
            if (pickedUri == null) {
                selectSimRingtone(uri = "silent", title = "Silent (No Sound)", playPreview = false)
                return@launch
            }
            val (persistedUri, title) = withContext(Dispatchers.IO) {
                val resolvedTitle = ringtoneEngine.resolveTitleForUri(pickedUri)
                val storedUri = ringtoneEngine.persistRingtoneUriIfNeeded(
                    sourceUri = pickedUri,
                    forceCopy = isCustomAudio
                )
                storedUri to resolvedTitle
            }
            prefsRepo.setSimRingtone(persistedUri, title)
            showMessage("Incoming call ringtone set to $title")
            startRingtonePreview(
                uri = persistedUri,
                vibrate = settings.value.ringtones.vibrateOnSimRing
            )
        }
    }

    private fun startRingtonePreview(uri: String, vibrate: Boolean) {
        if (uri == "silent" || uri.isBlank()) {
            stopRingtonePreview()
            return
        }
        _previewingRingtoneUri.value = uri
        ringtoneEngine.playPreviewOrRing(
            uriString = uri,
            looping = false,
            vibrate = vibrate,
            respectRingerMode = false
        ) {
            if (_previewingRingtoneUri.value == uri) {
                _previewingRingtoneUri.value = null
            }
        }
    }

    fun toggleRingtonePreview(uri: String, vibrate: Boolean = false) {
        if (_previewingRingtoneUri.value == uri) {
            stopRingtonePreview()
            return
        }
        startRingtonePreview(uri = uri, vibrate = vibrate)
    }

    fun stopRingtonePreview() {
        ringtoneEngine.stop()
        _previewingRingtoneUri.value = null
    }

    fun setVibrateOnSimRing(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setVibrateOnSimRing(enabled)
    }

    fun resetRingtonesToDefault() = viewModelScope.launch {
        stopRingtonePreview()
        prefsRepo.resetRingtonesToDefault()
        showMessage("Incoming call ringtone restored to default.")
    }

    fun setSpamShield(enabled: Boolean) = viewModelScope.launch { prefsRepo.setSpamShieldEnabled(enabled) }
    fun setAutoBlockSpam(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoBlockHighRiskSpam(enabled) }
    fun setScreenPrivacy(enabled: Boolean) = viewModelScope.launch { prefsRepo.setScreenPrivacySecure(enabled) }
    fun setDtmfTones(enabled: Boolean) = viewModelScope.launch { prefsRepo.setDtmfTonesEnabled(enabled) }

    fun wipeEncryptedVault() {
        viewModelScope.launch {
            ringtoneEngine.stop()
            callRecordingManager.stopPlayback()
            callRecordingManager.wipeAllRecordingFiles(
                recordings.value.map { it.fileName to it.storageUri }
            )
            repository.wipeAllLocalData()
            showMessage("Local vault and recordings cleared.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        val resolver = getApplication<Application>().contentResolver
        if (contactsObserverRegistered) {
            runCatching { resolver.unregisterContentObserver(contactsContentObserver) }
            contactsObserverRegistered = false
        }
        if (callLogObserverRegistered) {
            runCatching { resolver.unregisterContentObserver(callLogContentObserver) }
            callLogObserverRegistered = false
        }
        if (subscriptionsListenerRegistered) {
            val subManager = getApplication<Application>()
                .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            runCatching { subManager?.removeOnSubscriptionsChangedListener(subscriptionsChangedListener) }
            subscriptionsListenerRegistered = false
        }
        ringtoneEngine.release()
        callRecordingManager.release()
    }
}
