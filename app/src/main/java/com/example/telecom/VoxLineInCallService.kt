package com.example.telecom

import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SystemTelecomCallSnapshot(
    val handle: String,
    val state: Int,
    val isRinging: Boolean,
    val isDialing: Boolean,
    val isConnected: Boolean,
    val isHolding: Boolean,
    val isIncoming: Boolean,
    val supportsRespondViaText: Boolean = false,
    val cannedTextResponses: List<String> = emptyList()
)

/**
 * Process-wide bridge between Android Telecom's InCallService and VoxLine's Compose UI.
 * Routes all call control actions (answer, reject, hold, mute, speaker, DTMF, disconnect)
 * directly to the real system Telecom Call and bound InCallService.
 */
object ActiveTelecomCallBus {
    private val _systemCall = MutableStateFlow<SystemTelecomCallSnapshot?>(null)
    val systemCall: StateFlow<SystemTelecomCallSnapshot?> = _systemCall.asStateFlow()

    @Volatile
    private var currentCall: Call? = null

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
        currentCall = call
        emitSnapshot(call)
    }

    fun updateCall(call: Call) {
        if (currentCall == call) {
            emitSnapshot(call)
        }
    }

    fun detachCall(call: Call) {
        if (currentCall == call) {
            currentCall = null
            _systemCall.value = null
        }
    }

    fun answer() {
        currentCall?.answer(0)
    }

    fun reject() {
        val call = currentCall ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call.reject(Call.REJECT_REASON_DECLINED)
        } else {
            @Suppress("DEPRECATION")
            call.reject(false, null)
        }
    }

    /**
     * Rejects an incoming ringing Telecom call with a Quick Reply SMS message using Android's
     * native Call.reject(rejectWithMessage = true, textMessage) Respond-Via-SMS API.
     * Returns true if Telecom accepted the native quick-reply dispatch.
     */
    fun rejectWithMessage(message: String): Boolean {
        val call = currentCall ?: return false
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) {
            reject()
            return false
        }
        val canRespondViaText = call.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        return try {
            if (canRespondViaText) {
                call.reject(true, cleanMessage)
                true
            } else {
                reject()
                false
            }
        } catch (_: Exception) {
            runCatching { reject() }
            false
        }
    }

    fun disconnect() {
        val call = currentCall ?: return
        @Suppress("DEPRECATION")
        if (call.state == Call.STATE_RINGING) {
            reject()
        } else {
            call.disconnect()
        }
    }

    fun hold(shouldHold: Boolean) {
        if (shouldHold) currentCall?.hold() else currentCall?.unhold()
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
        currentCall?.playDtmfTone(digit)
        currentCall?.stopDtmfTone()
    }

    private fun emitSnapshot(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        @Suppress("DEPRECATION")
        val state = call.state
        val isIncoming = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details?.callDirection == Call.Details.DIRECTION_INCOMING
        } else {
            state == Call.STATE_RINGING
        }
        val canRespondViaText = call.details?.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) == true
        val canned = runCatching { call.cannedTextResponses.orEmpty() }.getOrDefault(emptyList())
        _systemCall.value = SystemTelecomCallSnapshot(
            handle = number,
            state = state,
            isRinging = state == Call.STATE_RINGING,
            isDialing = state == Call.STATE_DIALING || state == Call.STATE_CONNECTING,
            isConnected = state == Call.STATE_ACTIVE,
            isHolding = state == Call.STATE_HOLDING,
            isIncoming = isIncoming,
            supportsRespondViaText = canRespondViaText,
            cannedTextResponses = canned
        )
    }
}

/**
 * Standard Android Telecom InCallService allowing VoxLine to act as the
 * system Default Phone / Dialer application for real carrier SIM calls.
 */
class VoxLineInCallService : InCallService() {

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            ActiveTelecomCallBus.updateCall(call)
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        ActiveTelecomCallBus.attachService(this)
        call.registerCallback(callCallback)
        ActiveTelecomCallBus.attachCall(call)

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("extra_open_active_call", true)
        }
        startActivity(launchIntent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        ActiveTelecomCallBus.detachCall(call)
    }

    override fun onDestroy() {
        ActiveTelecomCallBus.detachService(this)
        super.onDestroy()
    }
}
