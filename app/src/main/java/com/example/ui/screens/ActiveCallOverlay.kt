package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ContactEntity
import com.example.telecom.CallRecordingAccessibilityService
import com.example.telecom.PhoneCountryResolver
import com.example.telecom.SpamRiskLevel
import com.example.telecom.extractSimCompanyName
import com.example.ui.components.AddConferenceCallerDialog
import com.example.ui.components.ConferenceParticipantsDialog
import com.example.ui.components.ContactAvatar
import com.example.ui.components.IncomingCallSlideControl
import com.example.ui.components.MetricPill
import com.example.ui.components.QualityTrendChart
import com.example.ui.components.SpamRiskChip
import com.example.ui.components.formatDuration
import com.example.ui.components.mosQualityColor
import com.example.ui.theme.AccentSkyBlue
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent
import com.example.ui.theme.QualityFair
import com.example.ui.theme.SimAccentEmerald
import com.example.viewmodel.ActiveCallSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveCallOverlay(
    session: ActiveCallSession,
    contacts: List<ContactEntity> = emptyList(),
    quickReplyTemplates: List<String>,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onToggleRecord: (hasAudioPermission: Boolean) -> Unit,
    onRetryRecord: () -> Unit = {},
    onRecordPermissionDenied: (permanentlyDenied: Boolean) -> Unit,
    onSendDtmf: (Char) -> Unit,
    onAddCallToConference: (String) -> Unit = {},
    onMergeCalls: () -> Unit = {},
    onSwapCalls: () -> Unit = {},
    onSeparateParticipant: (String) -> Unit = {},
    onDisconnectParticipant: (String) -> Unit = {},
    onAnswerCall: () -> Unit,
    onDeclineCall: () -> Unit,
    onDeclineWithQuickReply: (String) -> Unit,
    onEndCall: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findActivity() }
    val haptics = LocalHapticFeedback.current

    var showDtmfPad by remember { mutableStateOf(false) }
    var showQuickReplySheet by remember { mutableStateOf(false) }
    var customQuickReply by remember { mutableStateOf("") }
    var showAddCallerDialog by remember { mutableStateOf(false) }
    var showConferenceParticipantsDialog by remember { mutableStateOf(false) }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasRequestedMicOnce by remember { mutableStateOf(false) }
    var showMicRationaleDialog by remember { mutableStateOf(false) }
    var showMicSettingsDialog by remember { mutableStateOf(false) }
    var isAudioHelperEnabled by remember {
        mutableStateOf(CallRecordingAccessibilityService.isServiceEnabled(context))
    }

    // Refresh microphone permission & Call Audio Helper state when returning from Android Settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val wasHelperEnabled = isAudioHelperEnabled
                val nowHelperEnabled = CallRecordingAccessibilityService.isServiceEnabled(context)
                isAudioHelperEnabled = nowHelperEnabled
                if (!wasHelperEnabled && nowHelperEnabled && session.isRecording && session.isRecordingSilenced) {
                    onRetryRecord()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedMicOnce = true
        hasMicPermission = granted
        if (granted) {
            onToggleRecord(true)
        } else {
            val shouldShowRationale = activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
            } ?: false
            val permanentlyDenied = !shouldShowRationale
            if (permanentlyDenied) {
                showMicSettingsDialog = true
            }
            onRecordPermissionDenied(permanentlyDenied)
        }
    }

    val handleRecordToggleClick: () -> Unit = {
        if (session.isRecording) {
            onToggleRecord(true)
        } else {
            val grantedNow = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            hasMicPermission = grantedNow
            if (grantedNow) {
                onToggleRecord(true)
            } else {
                val shouldShowRationale = activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO)
                } ?: false
                when {
                    shouldShowRationale -> showMicRationaleDialog = true
                    hasRequestedMicOnce -> showMicSettingsDialog = true
                    else -> recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    val mergedQuickReplies = remember(quickReplyTemplates, session.cannedQuickReplies) {
        (quickReplyTemplates + session.cannedQuickReplies)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
    }

    val pulseTransition = rememberInfiniteTransition(label = "call_pulse")
    val recAlpha by pulseTransition.animateFloat(
        initialValue = 0.30f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "rec_alpha"
    )
    val haloAlpha by pulseTransition.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.36f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "halo_alpha"
    )

    val latestSample = session.qualitySamples.lastOrNull()
    val mosSeries = session.qualitySamples.map { it.mosScore }
    val currentMos = latestSample?.mosScore ?: 0f
    val mosColor = if (currentMos > 0f) mosQualityColor(currentMos) else AccentSkyBlue
    val lineAccent = SimAccentEmerald
    val simCompanyName = remember(session.routeLabel) { extractSimCompanyName(session.routeLabel) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060D17))
    ) {
        // Generated ambient acoustic backdrop
        Image(
            painter = painterResource(id = R.drawable.img_call_ambient_wave_1795059046281),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.50f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x88060D17),
                            Color(0xCC060D17),
                            Color(0xF7060D17)
                        )
                    )
                )
        )

        if (session.isRinging) {
            // Focused Incoming Call View: Caller Hero + Quick Reply + Slide to Answer / Reject
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .widthIn(max = 560.dp)
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Status Row (shown only when a SIM carrier/company name is available)
                if (simCompanyName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = lineAccent.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, lineAccent.copy(alpha = 0.45f)),
                            modifier = Modifier.testTag("incoming_sim_company_badge")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SimCard,
                                    contentDescription = "Carrier $simCompanyName",
                                    tint = lineAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = simCompanyName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                // Incoming Caller Identity Hero
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .clip(CircleShape)
                                .background(lineAccent.copy(alpha = haloAlpha))
                        )
                        ContactAvatar(
                            name = session.displayName.ifBlank { session.address },
                            colorIndex = 0,
                            photoUri = session.photoUri,
                            size = 86.dp
                        )
                    }

                    Text(
                        text = session.displayName.ifBlank { session.address },
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (session.displayName.isNotBlank()) {
                        Text(
                            text = session.address,
                            style = MonoMetricStyle,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    val callingCountry = remember(session.address) {
                        PhoneCountryResolver.resolveCountryName(context, session.address)
                    }
                    if (callingCountry.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.testTag("active_call_country")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Public,
                                contentDescription = "Calling country",
                                tint = lineAccent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = callingCountry,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = lineAccent.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, lineAccent.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = session.statusLabel.ifBlank { "Incoming Call" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = lineAccent,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                        )
                    }
                }

                // Bottom Incoming Action Deck: Quick Reply SMS + Slide to Answer / Reject
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showQuickReplySheet = true
                        },
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = AccentSkyBlue.copy(alpha = 0.20f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("incoming_quick_reply_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Message,
                            contentDescription = "Send Quick Reply SMS",
                            tint = AccentSkyBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Quick Reply via SMS",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    IncomingCallSlideControl(
                        onAnswer = onAnswerCall,
                        onReject = onDeclineCall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // Active / Connected / Dialing Call View with all calling-time options
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .widthIn(max = 560.dp)
                    .align(Alignment.Center)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Status Row: SIM Company Name (when available) & Live Recording Pill
                if (simCompanyName.isNotBlank() || session.isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (simCompanyName.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = lineAccent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, lineAccent.copy(alpha = 0.45f)),
                                modifier = Modifier.testTag("active_call_sim_company_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SimCard,
                                        contentDescription = "Carrier $simCompanyName",
                                        tint = lineAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = simCompanyName,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (session.isRecording) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = CallEndRed.copy(alpha = 0.24f),
                                border = BorderStroke(1.dp, CallEndRed.copy(alpha = 0.6f)),
                                modifier = Modifier.testTag("active_call_recording_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FiberManualRecord,
                                        contentDescription = "Recording active",
                                        tint = CallEndRed,
                                        modifier = Modifier
                                            .size(10.dp)
                                            .alpha(recAlpha)
                                    )
                                    Text(
                                        text = "${if (session.isAutoRecording) "AUTO REC" else "REC"} ${formatDuration(session.recordingSec)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // Caller Identity Hero with Animated Pulse Ring
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(CircleShape)
                                .background(lineAccent.copy(alpha = if (session.isOnHold) 0.08f else haloAlpha))
                        )
                        if (session.isConference) {
                            Surface(
                                shape = CircleShape,
                                color = SimAccentEmerald.copy(alpha = 0.25f),
                                border = BorderStroke(1.5.dp, SimAccentEmerald),
                                modifier = Modifier.size(70.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Groups,
                                        contentDescription = "Conference Call",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        } else {
                            ContactAvatar(
                                name = session.displayName.ifBlank { session.address },
                                colorIndex = 0,
                                photoUri = session.photoUri,
                                size = 70.dp
                            )
                        }
                    }

                    Text(
                        text = if (session.isConference) {
                            "Conference Call"
                        } else {
                            session.displayName.ifBlank { session.address }
                        },
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (session.isConference) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SimAccentEmerald.copy(alpha = 0.20f),
                            border = BorderStroke(1.dp, SimAccentEmerald.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { showConferenceParticipantsDialog = true }
                                .testTag("manage_conference_participants_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Groups,
                                    contentDescription = "Manage conference participants",
                                    tint = SimAccentEmerald,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Manage ${session.conferenceParticipants.size.coerceAtLeast(2)} Participants",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }
                    } else {
                        if (session.displayName.isNotBlank()) {
                            Text(
                                text = session.address,
                                style = MonoMetricStyle,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        val callingCountry = remember(session.address) {
                            PhoneCountryResolver.resolveCountryName(context, session.address)
                        }
                        if (callingCountry.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("active_call_country")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = "Calling country",
                                    tint = lineAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = callingCountry,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }

                    // Live Timer + State Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatDuration(session.elapsedSec),
                            style = MaterialTheme.typography.titleLarge,
                            color = if (session.isOnHold) QualityFair else lineAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("active_call_timer")
                        )
                        Text(
                            text = "• ${session.statusLabel}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (session.isOnHold) QualityFair else Color(0xFFCBD5E1),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Active Status Chips (Muted / On Hold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (session.isMuted) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AccentSkyBlue.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "MIC MUTED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentSkyBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        if (session.isOnHold) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = QualityFair.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ON HOLD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = QualityFair,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Secondary / Held Call Banner with instant Swap & Merge Conference buttons
                session.secondaryCall?.let { secondary ->
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xCC132238),
                        border = BorderStroke(1.dp, AccentSkyBlue.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("secondary_held_call_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (secondary.isHolding) "ON HOLD" else "SECOND CALL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = QualityFair
                                )
                                Text(
                                    text = secondary.displayName.ifBlank { secondary.handle },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (session.canSwapCalls) {
                                    FilledTonalButton(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSwapCalls()
                                        },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = AccentSkyBlue.copy(alpha = 0.24f),
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("banner_swap_calls_button")
                                    ) {
                                        Icon(Icons.Filled.SwapCalls, contentDescription = "Swap calls", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Swap", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (session.canMergeCalls) {
                                    Button(
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onMergeCalls()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SimAccentEmerald,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("banner_merge_calls_button")
                                    ) {
                                        Icon(Icons.Filled.CallMerge, contentDescription = "Merge conference", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Merge", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Real-Time Call Quality Analytics HUD Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC0E1726)),
                    border = BorderStroke(1.dp, Color(0xFF23344D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.GraphicEq,
                                    contentDescription = "Live call telemetry",
                                    tint = mosColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Live Voice Quality",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = latestSample?.codecLabel ?: "Measuring...",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }

                        QualityTrendChart(
                            values = mosSeries,
                            lineColor = mosColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill(
                                label = "MOS",
                                value = if (currentMos > 0f) "%.2f".format(currentMos) else "--",
                                accent = mosColor,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "RTT",
                                value = latestSample?.let { "${it.latencyMs}ms" } ?: "--",
                                accent = AccentSkyBlue,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "JITTER",
                                value = latestSample?.let { "${it.jitterMs}ms" } ?: "--",
                                accent = QualityExcellent,
                                modifier = Modifier.weight(1f)
                            )
                            MetricPill(
                                label = "LOSS",
                                value = latestSample?.let { "%.1f%%".format(it.packetLossPct) } ?: "--",
                                accent = if ((latestSample?.packetLossPct ?: 0f) > 2f) CallEndRed else Color(0xFFCBD5E1),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Optional In-Call DTMF Keypad
                AnimatedVisibility(visible = showDtmfPad) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xE6101B2D),
                        border = BorderStroke(1.dp, Color(0xFF23344D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val dtmfRows = listOf("123", "456", "789", "*0#")
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            dtmfRows.forEach { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    r.forEach { ch ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFF1D2C44),
                                            modifier = Modifier
                                                .size(width = 82.dp, height = 46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onSendDtmf(ch)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = ch.toString(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Material Design 3 Active Call Controls Deck
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xE00E1726)),
                    border = BorderStroke(1.dp, Color(0xFF23344D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row 1: Mute, Hold, Speaker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryCallControlTile(
                                icon = if (session.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                label = if (session.isMuted) "Unmute" else "Mute",
                                active = session.isMuted,
                                activeColor = AccentSkyBlue,
                                tag = "call_mute_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleMute()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryCallControlTile(
                                icon = if (session.isOnHold) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                label = if (session.isOnHold) "Resume" else "Hold",
                                active = session.isOnHold,
                                activeColor = QualityFair,
                                tag = "call_hold_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleHold()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryCallControlTile(
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                label = if (session.isSpeakerOn) "Speaker On" else "Speaker",
                                active = session.isSpeakerOn,
                                activeColor = SimAccentEmerald,
                                tag = "call_speaker_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleSpeaker()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Row 2: Record, Keypad, Add Call
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryCallControlTile(
                                icon = if (session.isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                label = when {
                                    session.isRecordingBusy -> "Saving..."
                                    session.isRecording -> "Stop Rec"
                                    else -> "Record"
                                },
                                active = session.isRecording,
                                activeColor = CallEndRed,
                                isLoading = session.isRecordingBusy,
                                pulseIconAlpha = if (session.isRecording) recAlpha else 1f,
                                tag = "call_record_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    handleRecordToggleClick()
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryCallControlTile(
                                icon = Icons.Filled.Dialpad,
                                label = if (showDtmfPad) "Hide Pad" else "Keypad",
                                active = showDtmfPad,
                                activeColor = Color(0xFFA78BFA),
                                tag = "call_dtmf_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showDtmfPad = !showDtmfPad
                                },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryCallControlTile(
                                icon = Icons.Filled.AddIcCall,
                                label = "Add Call",
                                active = false,
                                activeColor = SimAccentEmerald,
                                tag = "call_add_caller_button",
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAddCallerDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Row 3: Conference Merge / Manage & Swap Calls (shown when multi-call/conference active)
                        if (session.isConference || session.canMergeCalls || session.canSwapCalls || session.secondaryCall != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (session.isConference && !session.canMergeCalls) {
                                    PrimaryCallControlTile(
                                        icon = Icons.Filled.Groups,
                                        label = "Manage (${session.conferenceParticipants.size.coerceAtLeast(2)})",
                                        active = true,
                                        activeColor = SimAccentEmerald,
                                        tag = "call_manage_conference_button",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showConferenceParticipantsDialog = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    PrimaryCallControlTile(
                                        icon = Icons.Filled.CallMerge,
                                        label = "Merge",
                                        active = session.canMergeCalls,
                                        activeColor = SimAccentEmerald,
                                        enabled = session.canMergeCalls,
                                        tag = "call_merge_button",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onMergeCalls()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (session.canSwapCalls) {
                                    PrimaryCallControlTile(
                                        icon = Icons.Filled.SwapCalls,
                                        label = "Swap Calls",
                                        active = false,
                                        activeColor = AccentSkyBlue,
                                        tag = "call_swap_button",
                                        onClick = {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSwapCalls()
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // End Call Action Button
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onEndCall()
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CallEndRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(58.dp)
                                .testTag("end_call_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CallEnd,
                                contentDescription = "End Call",
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "End Call",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Animated Incoming Call Quick Reply SMS Picker Sheet
        AnimatedVisibility(
            visible = session.isRinging && showQuickReplySheet,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xB3040A12))
                    .clickable { showQuickReplySheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = Color(0xFF0E1726),
                    border = BorderStroke(1.dp, Color(0xFF283A56)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 560.dp)
                        .safeDrawingPadding()
                        .clickable(enabled = false) {}
                        .testTag("quick_reply_sheet")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quick Reply via SMS",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Decline & text ${session.displayName.ifBlank { session.address }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { showQuickReplySheet = false },
                                modifier = Modifier.testTag("quick_reply_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close Quick Reply",
                                    tint = Color(0xFFCBD5E1)
                                )
                            }
                        }

                        // Predefined Quick Reply Options
                        mergedQuickReplies.forEachIndexed { index, replyText ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF162235),
                                border = BorderStroke(1.dp, Color(0xFF283A56)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showQuickReplySheet = false
                                        onDeclineWithQuickReply(replyText)
                                    }
                                    .testTag("quick_reply_option_$index")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = replyText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 10.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send quick reply",
                                        tint = AccentSkyBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Custom One-Off SMS Composer
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customQuickReply,
                                onValueChange = { if (it.length <= 160) customQuickReply = it },
                                placeholder = {
                                    Text(
                                        text = "Write a custom reply...",
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = AccentSkyBlue,
                                    unfocusedBorderColor = Color(0xFF283A56),
                                    cursorColor = AccentSkyBlue
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_reply_custom_input")
                            )
                            Button(
                                onClick = {
                                    if (customQuickReply.isNotBlank()) {
                                        val msg = customQuickReply.trim()
                                        customQuickReply = ""
                                        showQuickReplySheet = false
                                        onDeclineWithQuickReply(msg)
                                    }
                                },
                                enabled = customQuickReply.isNotBlank(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentSkyBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("quick_reply_send_custom_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send custom reply",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Microphone Runtime Permission Rationale Dialog
        if (showMicRationaleDialog) {
            AlertDialog(
                onDismissRequest = { showMicRationaleDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = CallEndRed
                    )
                },
                title = { Text(stringResource(R.string.recording_permission_title)) },
                text = { Text(stringResource(R.string.recording_permission_rationale)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showMicRationaleDialog = false
                            recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.testTag("record_permission_allow_button")
                    ) {
                        Text("Allow Microphone")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showMicRationaleDialog = false
                            onRecordPermissionDenied(false)
                        },
                        modifier = Modifier.testTag("record_permission_deny_button")
                    ) {
                        Text("Not Now")
                    }
                },
                modifier = Modifier.testTag("record_permission_rationale_dialog")
            )
        }

        // Permanently Denied Microphone Permission -> Open App Settings Dialog
        if (showMicSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showMicSettingsDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Filled.MicOff,
                        contentDescription = null,
                        tint = CallEndRed
                    )
                },
                title = { Text(stringResource(R.string.recording_permission_title)) },
                text = { Text(stringResource(R.string.recording_permission_settings_body)) },
                confirmButton = {
                    Button(
                        onClick = {
                            showMicSettingsDialog = false
                            val settingsIntent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            runCatching { context.startActivity(settingsIntent) }
                        },
                        modifier = Modifier.testTag("record_permission_open_settings_button")
                    ) {
                        Text("Open App Settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showMicSettingsDialog = false },
                        modifier = Modifier.testTag("record_permission_settings_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                },
                modifier = Modifier.testTag("record_permission_settings_dialog")
            )
        }
        if (showAddCallerDialog) {
            AddConferenceCallerDialog(
                contacts = contacts,
                onDialAdditionalCaller = { number ->
                    showAddCallerDialog = false
                    onAddCallToConference(number)
                },
                onDismiss = { showAddCallerDialog = false }
            )
        }

        if (showConferenceParticipantsDialog) {
            ConferenceParticipantsDialog(
                participants = session.conferenceParticipants,
                onSeparateParticipant = { callId ->
                    showConferenceParticipantsDialog = false
                    onSeparateParticipant(callId)
                },
                onDisconnectParticipant = onDisconnectParticipant,
                onDismiss = { showConferenceParticipantsDialog = false }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PrimaryCallControlTile(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    pulseIconAlpha: Float = 1f
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (active) activeColor.copy(alpha = 0.22f) else Color(0xFF162235),
        border = BorderStroke(
            width = if (active) 1.5.dp else 1.dp,
            color = if (active) activeColor else Color(0xFF283A56)
        ),
        modifier = modifier
            .height(74.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .testTag(tag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = if (active) activeColor else Color.White,
                    strokeWidth = 2.2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) activeColor else Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .alpha(pulseIconAlpha)
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) activeColor else Color(0xFFE2E8F0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
