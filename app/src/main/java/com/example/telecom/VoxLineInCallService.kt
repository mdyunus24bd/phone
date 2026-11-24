package com.example.telecom

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import com.example.MainActivity
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.CallType
import com.example.data.local.VoxLineDatabase
import com.example.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ConferenceParticipantSnapshot(
    val callId: String,
    val handle: String,
    val displayName: String,
    val canSeparate: Boolean,
    val canDisconnect: Boolean
)

data class SecondaryCallSnapshot(
    val callId: String,
    val handle: String,
    val displayName: String,
    val isHolding: Boolean,
    val isRinging: Boolean,
    val isConference: Boolean
)

data class SystemTelecomCallSnapshot(
    val handle: String,
    val state: Int,
    val isRinging: Boolean,
    val isDialing: Boolean,
    val isConnected: Boolean,
    val isHolding: Boolean,
    val isIncoming: Boolean,
    val supportsRespondViaText: Boolean = false,
    val cannedTextResponses: List<String> = emptyList(),
    val isConference: Boolean = false,
    val conferenceParticipants: List<ConferenceParticipantSnapshot> = emptyList(),
    val secondaryCall: SecondaryCallSnapshot? = null,
    val canMergeCalls: Boolean = false,
    val canSwapCalls: Boolean = false,
    val phoneAccountId: String = "",
    val phoneAccountLabel: String = ""
)

/**
 * Process-wide bridge between Android Telecom's InCallService and VoxLine's Compose UI.
 * Tracks single calls, multi-call hold/swap, and carrier SIM conference calls, routing all
 * actions (answer, reject, hold, mute, speaker, DTMF, merge, swap, split, disconnect)
 * directly to real system Telecom Call objects.
 */
object ActiveTelecomCallBus {
    private val _systemCall = MutableStateFlow<SystemTelecomCallSnapshot?>(null)
    val systemCall: StateFlow<SystemTelecomCallSnapshot?> = _systemCall.asStateFlow()

    private val lock = Any()
    private val trackedCalls = mutableListOf<Call>()

    @Volatile
    private var activeService: VoxLineInCallService? = null

    fun attachService(service: VoxLineInCallService) {
        activeService = service
    }

    fun detachService(service: VoxLineInCallService) {
        if (activeService == service) {
            activeService = null
        }
    }

    fun attachCall(call: Call) {
        synchronized(lock) {
            if (!trackedCalls.contains(call)) {
                trackedCalls.add(call)
            }
            emitSnapshotLocked()
        }
    }

    fun updateCall(call: Call) {
        synchronized(lock) {
            if (!trackedCalls.contains(call)) {
                trackedCalls.add(call)
            }
            emitSnapshotLocked()
        }
    }

    fun detachCall(call: Call) {
        synchronized(lock) {
            trackedCalls.remove(call)
            emitSnapshotLocked()
        }
    }

    fun hasRingingCall(): Boolean = synchronized(lock) {
        findRingingCallLocked() != null
    }

    @Suppress("DEPRECATION")
    fun hasActiveNonRingingCall(excluding: Call? = null): Boolean = synchronized(lock) {
        topLevelLiveCallsLocked().any { call ->
            call !== excluding && (
                call.state == Call.STATE_ACTIVE ||
                    call.state == Call.STATE_DIALING ||
                    call.state == Call.STATE_CONNECTING ||
                    call.state == Call.STATE_HOLDING
                )
        }
    }

    fun answer() {
        IncomingCallRinger.stop()
        val target = synchronized(lock) {
            findRingingCallLocked() ?: findPrimaryCallLocked()
        } ?: return
        runCatching { target.answer(0) }
    }

    fun reject() {
        IncomingCallRinger.stop()
        val target = synchronized(lock) {
            findRingingCallLocked() ?: findPrimaryCallLocked()
        } ?: return
        rejectCallInternal(target)
    }

    /**
     * Rejects an incoming ringing Telecom call with a Quick Reply SMS message using Android's
     * native Call.reject(rejectWithMessage = true, textMessage) Respond-Via-SMS API.
     */
    fun rejectWithMessage(message: String): Boolean {
        IncomingCallRinger.stop()
        val call = synchronized(lock) {
            findRingingCallLocked() ?: findPrimaryCallLocked()
        } ?: return false
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) {
            rejectCallInternal(call)
            return false
        }
        val canRespondViaText = call.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        return try {
            if (canRespondViaText) {
                call.reject(true, cleanMessage)
                true
            } else {
                rejectCallInternal(call)
                false
            }
        } catch (_: Exception) {
            runCatching { rejectCallInternal(call) }
            false
        }
    }

    fun disconnect() {
        IncomingCallRinger.stop()
        val call = synchronized(lock) { findPrimaryCallLocked() } ?: return
        @Suppress("DEPRECATION")
        if (call.state == Call.STATE_RINGING) {
            rejectCallInternal(call)
        } else {
            runCatching { call.disconnect() }
        }
    }

    fun hold(shouldHold: Boolean) {
        val call = synchronized(lock) { findPrimaryCallLocked() } ?: return
        runCatching {
            if (shouldHold) call.hold() else call.unhold()
        }
    }

    /**
     * Merges the active call with a held or conferenceable call into a carrier conference call.
     * Returns true if a merge/conference request was dispatched to Android Telecom.
     */
    fun mergeCalls(): Boolean {
        val (primary, secondary) = synchronized(lock) {
            val p = findPrimaryCallLocked()
            val s = findSecondaryCallLocked(p)
            p to s
        }
        if (primary == null) return false

        return runCatching {
            val details = primary.details
            if (details != null && details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                primary.mergeConference()
                return@runCatching true
            }
            val conferenceable = primary.conferenceableCalls.firstOrNull()
            if (conferenceable != null) {
                primary.conference(conferenceable)
                return@runCatching true
            }
            if (secondary != null) {
                val secDetails = secondary.details
                if (secDetails != null && secDetails.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) {
                    secondary.mergeConference()
                    return@runCatching true
                }
                val secConf = secondary.conferenceableCalls.firstOrNull()
                if (secConf != null) {
                    secondary.conference(secConf)
                    return@runCatching true
                }
                primary.conference(secondary)
                return@runCatching true
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Swaps between the active call and a held secondary call (or invokes carrier swapConference).
     * Returns true if a swap action was dispatched to Android Telecom.
     */
    fun swapCalls(): Boolean {
        val (primary, secondary) = synchronized(lock) {
            val p = findPrimaryCallLocked()
            val s = findSecondaryCallLocked(p)
            p to s
        }
        if (primary == null) return false

        return runCatching {
            val details = primary.details
            if (details != null && details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) {
                primary.swapConference()
                return@runCatching true
            }
            if (secondary != null) {
                @Suppress("DEPRECATION")
                if (secondary.state == Call.STATE_HOLDING) {
                    secondary.unhold()
                    return@runCatching true
                }
                @Suppress("DEPRECATION")
                if (primary.state == Call.STATE_ACTIVE) {
                    primary.hold()
                    return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Splits a single participant out of an active carrier conference into a private 1-on-1 call.
     */
    fun separateConferenceParticipant(callId: String): Boolean {
        val target = synchronized(lock) { findCallByIdLocked(callId) } ?: return false
        return runCatching {
            target.splitFromConference()
            true
        }.getOrDefault(false)
    }

    /**
     * Disconnects a single participant from an active carrier conference without dropping other callers.
     */
    fun disconnectConferenceParticipant(callId: String): Boolean {
        val target = synchronized(lock) { findCallByIdLocked(callId) } ?: return false
        return runCatching {
            target.disconnect()
            true
        }.getOrDefault(false)
    }

    fun setMuted(muted: Boolean) {
        activeService?.setMuted(muted)
    }

    @Suppress("DEPRECATION")
    fun setSpeaker(enabled: Boolean) {
        val route = if (enabled) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        activeService?.setAudioRoute(route)
    }

    fun playDtmf(digit: Char) {
        val call = synchronized(lock) { findPrimaryCallLocked() } ?: return
        runCatching {
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }
    }

    private fun rejectCallInternal(call: Call) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                @Suppress("DEPRECATION")
                call.reject(false, null)
            }
        }
    }

    private fun callIdentity(call: Call): String = System.identityHashCode(call).toString()

    private fun isConferenceCall(call: Call): Boolean {
        val hasProp = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true
        return hasProp || call.children.isNotEmpty()
    }

    private fun resolveDisplayName(call: Call): String {
        val contactName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call.details?.contactDisplayName.orEmpty()
        } else {
            ""
        }
        if (contactName.isNotBlank()) return contactName
        val callerDisplay = call.details?.callerDisplayName.orEmpty()
        if (callerDisplay.isNotBlank()) return callerDisplay
        return call.details?.handle?.schemeSpecificPart.orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun topLevelLiveCallsLocked(): List<Call> {
        return trackedCalls.filter { call ->
            call.parent == null &&
                call.state != Call.STATE_DISCONNECTED &&
                call.state != Call.STATE_DISCONNECTING
        }
    }

    @Suppress("DEPRECATION")
    private fun findRingingCallLocked(): Call? {
        return topLevelLiveCallsLocked().firstOrNull { it.state == Call.STATE_RINGING }
    }

    @Suppress("DEPRECATION")
    private fun findPrimaryCallLocked(): Call? {
        val topLevel = topLevelLiveCallsLocked()
        if (topLevel.isEmpty()) return null
        return topLevel.firstOrNull { it.state == Call.STATE_RINGING }
            ?: topLevel.firstOrNull { it.state == Call.STATE_DIALING || it.state == Call.STATE_CONNECTING }
            ?: topLevel.firstOrNull { it.state == Call.STATE_ACTIVE }
            ?: topLevel.firstOrNull { it.state == Call.STATE_HOLDING }
            ?: topLevel.first()
    }

    private fun findSecondaryCallLocked(primary: Call?): Call? {
        if (primary == null) return null
        return topLevelLiveCallsLocked().firstOrNull { it !== primary }
    }

    private fun findCallByIdLocked(callId: String): Call? {
        trackedCalls.firstOrNull { callIdentity(it) == callId }?.let { return it }
        trackedCalls.forEach { parent ->
            parent.children.firstOrNull { callIdentity(it) == callId }?.let { return it }
        }
        return null
    }

    private fun emitSnapshotLocked() {
        val primary = findPrimaryCallLocked()
        if (primary == null) {
            _systemCall.value = null
            return
        }

        val secondary = findSecondaryCallLocked(primary)
        val isConf = isConferenceCall(primary)
        val rawHandle = primary.details?.handle?.schemeSpecificPart.orEmpty()
        val participants = if (isConf) {
            primary.children.map { child ->
                val childHandle = child.details?.handle?.schemeSpecificPart.orEmpty().ifBlank { "Participant" }
                ConferenceParticipantSnapshot(
                    callId = callIdentity(child),
                    handle = childHandle,
                    displayName = resolveDisplayName(child).ifBlank { childHandle },
                    canSeparate = child.details?.can(Call.Details.CAPABILITY_SEPARATE_FROM_CONFERENCE) == true,
                    canDisconnect = child.details?.can(Call.Details.CAPABILITY_DISCONNECT_FROM_CONFERENCE) != false
                )
            }
        } else {
            emptyList()
        }

        val secondarySnap = secondary?.let { sec ->
            @Suppress("DEPRECATION")
            val secState = sec.state
            val secHandle = sec.details?.handle?.schemeSpecificPart.orEmpty().ifBlank {
                if (isConferenceCall(sec)) "Conference Call" else "Unknown"
            }
            SecondaryCallSnapshot(
                callId = callIdentity(sec),
                handle = secHandle,
                displayName = if (isConferenceCall(sec)) {
                    "Conference (${sec.children.size.coerceAtLeast(2)})"
                } else {
                    resolveDisplayName(sec).ifBlank { secHandle }
                },
                isHolding = secState == Call.STATE_HOLDING,
                isRinging = secState == Call.STATE_RINGING,
                isConference = isConferenceCall(sec)
            )
        }

        @Suppress("DEPRECATION")
        val state = primary.state
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            primary.details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            state == Call.STATE_RINGING
        }
        val canRespondViaText = primary.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        val canned = runCatching { primary.cannedTextResponses.orEmpty() }.getOrDefault(emptyList())

        val canMerge = primary.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true ||
            primary.conferenceableCalls.isNotEmpty() ||
            (secondary != null && state != Call.STATE_RINGING && !secondarySnap!!.isRinging && (
                secondary.details?.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) == true ||
                    secondary.conferenceableCalls.isNotEmpty() ||
                    state == Call.STATE_ACTIVE
                ))

        val canSwap = primary.details?.can(Call.Details.CAPABILITY_SWAP_CONFERENCE) == true ||
            (secondary != null && state != Call.STATE_RINGING)

        val effectiveHandle = when {
            isConf -> "Conference (${participants.size.coerceAtLeast(2)})"
            rawHandle.isNotBlank() -> rawHandle
            else -> "Unknown"
        }

        val accountHandle = primary.details?.accountHandle
        val phoneAccountId = accountHandle?.id?.trim().orEmpty()
        val phoneAccountLabel = if (accountHandle != null) {
            runCatching {
                val telecom = activeService?.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecom?.getPhoneAccount(accountHandle)?.label?.toString()?.trim().orEmpty()
            }.getOrDefault("")
        } else {
            ""
        }

        _systemCall.value = SystemTelecomCallSnapshot(
            handle = effectiveHandle,
            state = state,
            isRinging = state == Call.STATE_RINGING,
            isDialing = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING,
            isConnected = state == Call.STATE_ACTIVE,
            isHolding = state == Call.STATE_HOLDING,
            isIncoming = isIncoming,
            supportsRespondViaText = canRespondViaText,
            cannedTextResponses = canned,
            isConference = isConf,
            conferenceParticipants = participants,
            secondaryCall = secondarySnap,
            canMergeCalls = canMerge,
            canSwapCalls = canSwap,
            phoneAccountId = phoneAccountId,
            phoneAccountLabel = phoneAccountLabel
        )
    }
}

/**
 * Standard Android Telecom InCallService allowing VoxLine to act as the
 * system Default Phone / Dialer application for real carrier SIM single & conference calls.
 */
class VoxLineInCallService : InCallService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val autoRejectedCalls = mutableSetOf<Call>()
    private val userSilencedCalls = mutableSetOf<Call>()
    private var lastSelfSilenceAtMs: Long = 0L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (call in autoRejectedCalls) return
            ActiveTelecomCallBus.updateCall(call)
            syncCallDisplayNotification(call, state)
            syncIncomingRingtone(call, state)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (call in autoRejectedCalls) return
            ActiveTelecomCallBus.updateCall(call)
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
            if (call in autoRejectedCalls) return
            ActiveTelecomCallBus.updateCall(call)
            @Suppress("DEPRECATION")
            syncCallDisplayNotification(call, call.state)
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) {
            if (call in autoRejectedCalls) return
            ActiveTelecomCallBus.updateCall(call)
        }

        override fun onParentChanged(call: Call, parent: Call?) {
            if (call in autoRejectedCalls) return
            ActiveTelecomCallBus.updateCall(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveTelecomCallBus.attachService(this)

        @Suppress("DEPRECATION")
        val initialState = call.state
        val isIncomingRinging = initialState == Call.STATE_RINGING || (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                call.details?.callDirection == Call.Details.DIRECTION_INCOMING &&
                initialState != Call.STATE_ACTIVE
            )

        if (!isIncomingRinging) {
            attachAndDisplayCall(call, initialState)
            return
        }

        val incomingNumber = call.details?.handle?.schemeSpecificPart.orEmpty()
        val callerName = resolveCallerName(call)

        serviceScope.launch {
            val matchedRule = withContext(Dispatchers.IO) {
                runCatching {
                    val rules = VoxLineDatabase.getInstance(applicationContext)
                        .spamRuleDao()
                        .getAllSnapshot()
                    SpamAnalyzer.findBlockingRule(incomingNumber, rules)
                }.getOrNull()
            }

            if (matchedRule != null) {
                autoRejectedCalls.add(call)
                rejectCallImmediately(call)
                withContext(Dispatchers.IO) {
                    runCatching {
                        val db = VoxLineDatabase.getInstance(applicationContext)
                        db.spamRuleDao().incrementMatches(matchedRule.id)
                        db.callHistoryDao().insert(
                            CallLogEntity(
                                phoneNumber = incomingNumber.ifBlank { matchedRule.pattern },
                                displayName = callerName,
                                callType = CallType.SIM,
                                direction = CallDirection.BLOCKED,
                                timestamp = System.currentTimeMillis(),
                                durationSec = 0L,
                                spamScore = matchedRule.riskScore,
                                spamReason = "Auto-rejected: ${matchedRule.label}"
                            )
                        )
                    }
                }
                return@launch
            }

            @Suppress("DEPRECATION")
            val currentState = call.state
            if (currentState != Call.STATE_DISCONNECTED && currentState != Call.STATE_DISCONNECTING) {
                attachAndDisplayCall(call, currentState)
            }
        }
    }

    private fun attachAndDisplayCall(call: Call, state: Int) {
        call.registerCallback(callCallback)
        ActiveTelecomCallBus.attachCall(call)
        syncCallDisplayNotification(call, state)
        syncIncomingRingtone(call, state)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra(IncomingCallNotificationManager.EXTRA_OPEN_ACTIVE_CALL, true)
        }
        runCatching { startActivity(launchIntent) }
    }

    private fun silenceSystemTelecomRinger() {
        runCatching {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return
            if (telecom.defaultDialerPackage == packageName) {
                lastSelfSilenceAtMs = SystemClock.elapsedRealtime()
                @Suppress("DEPRECATION")
                telecom.silenceRinger()
            }
        }
    }

    private fun syncIncomingRingtone(call: Call, state: Int) {
        if (state == Call.STATE_RINGING) {
            if (call in userSilencedCalls) return
            // Do not blast a full ringtone during call-waiting while another call is already active
            if (ActiveTelecomCallBus.hasActiveNonRingingCall(excluding = call)) return

            val callKey = System.identityHashCode(call).toString()
            silenceSystemTelecomRinger()

            serviceScope.launch {
                val ringtonePrefs = withContext(Dispatchers.IO) {
                    runCatching {
                        UserPreferencesRepository(applicationContext).getRingtonePreferencesSnapshot()
                    }.getOrNull()
                } ?: return@launch

                @Suppress("DEPRECATION")
                val stillRinging = call.state == Call.STATE_RINGING &&
                    call !in autoRejectedCalls &&
                    call !in userSilencedCalls &&
                    !ActiveTelecomCallBus.hasActiveNonRingingCall(excluding = call)

                if (stillRinging) {
                    IncomingCallRinger.startIncomingRing(
                        context = applicationContext,
                        callKey = callKey,
                        preferences = ringtonePrefs
                    )
                    // Second pass silence in case OEM Telecom started its system ringer asynchronously
                    delay(200L)
                    @Suppress("DEPRECATION")
                    if (call.state == Call.STATE_RINGING && call !in userSilencedCalls) {
                        silenceSystemTelecomRinger()
                    }
                }
            }
        } else {
            userSilencedCalls.remove(call)
            if (!ActiveTelecomCallBus.hasRingingCall()) {
                IncomingCallRinger.stop()
            }
        }
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        // Ignore the callback echo triggered by our own silenceSystemTelecomRinger() call
        if (SystemClock.elapsedRealtime() - lastSelfSilenceAtMs < 750L) {
            return
        }
        calls?.forEach { call ->
            @Suppress("DEPRECATION")
            if (call.state == Call.STATE_RINGING) {
                userSilencedCalls.add(call)
            }
        }
        IncomingCallRinger.stop()
    }

    private fun rejectCallImmediately(call: Call) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                @Suppress("DEPRECATION")
                call.reject(false, null)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        userSilencedCalls.remove(call)
        if (autoRejectedCalls.remove(call)) {
            return
        }
        call.unregisterCallback(callCallback)
        ActiveTelecomCallBus.detachCall(call)
        if (!ActiveTelecomCallBus.hasRingingCall()) {
            IncomingCallRinger.stop()
        }
        val remaining = ActiveTelecomCallBus.systemCall.value
        if (remaining == null) {
            IncomingCallNotificationManager.cancelCallNotification(this)
        } else {
            IncomingCallNotificationManager.showOngoingCall(
                context = this,
                phoneNumber = remaining.handle,
                callerName = remaining.handle,
                isHolding = remaining.isHolding
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        autoRejectedCalls.clear()
        userSilencedCalls.clear()
        IncomingCallRinger.stop()
        IncomingCallNotificationManager.cancelCallNotification(this)
        ActiveTelecomCallBus.detachService(this)
        super.onDestroy()
    }

    private fun syncCallDisplayNotification(call: Call, state: Int) {
        val isConf = call.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
            call.children.isNotEmpty()
        val number = if (isConf) {
            "Conference (${call.children.size.coerceAtLeast(2)} callers)"
        } else {
            call.details?.handle?.schemeSpecificPart.orEmpty()
        }
        val callerName = if (isConf) number else resolveCallerName(call)

        when (state) {
            Call.STATE_RINGING -> {
                IncomingCallNotificationManager.showIncomingCall(
                    context = this,
                    phoneNumber = number,
                    callerName = callerName
                )
            }

            Call.STATE_ACTIVE, Call.STATE_HOLDING, Call.STATE_DIALING, Call.STATE_CONNECTING -> {
                IncomingCallNotificationManager.showOngoingCall(
                    context = this,
                    phoneNumber = number,
                    callerName = callerName,
                    isHolding = state == Call.STATE_HOLDING
                )
            }

            Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING -> {
                if (ActiveTelecomCallBus.systemCall.value == null) {
                    IncomingCallNotificationManager.cancelCallNotification(this)
                }
            }
        }
    }

    private fun resolveCallerName(call: Call): String {
        val contactName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call.details?.contactDisplayName.orEmpty()
        } else {
            ""
        }
        if (contactName.isNotBlank()) return contactName
        val display = call.details?.callerDisplayName.orEmpty()
        if (display.isNotBlank()) return display
        return call.details?.handle?.schemeSpecificPart.orEmpty()
    }
}
