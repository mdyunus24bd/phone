package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CallAcceptGreen
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.SimAccentEmerald
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bidirectional slide control for incoming calls:
 * - Slide center handle RIGHT past the commit threshold to Answer.
 * - Slide center handle LEFT past the commit threshold to Reject.
 * Releasing before the threshold springs smoothly back to center.
 */
@Composable
fun IncomingCallSlideControl(
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val trackHeight = 78.dp
    val thumbSize = 64.dp
    val horizontalTrackPadding = 7.dp
    val commitThreshold = 0.65f

    var rawDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var thresholdCrossed by remember { mutableStateOf(false) }
    var actionTriggered by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        actionTriggered = false
        rawDragOffsetPx = 0f
    }

    val animatedOffsetPx by animateFloatAsState(
        targetValue = rawDragOffsetPx,
        animationSpec = if (isDragging) {
            spring(stiffness = Spring.StiffnessHigh, dampingRatio = Spring.DampingRatioNoBouncy)
        } else {
            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
        },
        label = "incoming_slide_offset"
    )

    val idlePulse = rememberInfiniteTransition(label = "incoming_slide_hint")
    val chevronWave by idlePulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "chevron_wave"
    )
    val thumbHaloScale by idlePulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "thumb_halo_scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Clear left / right directional labels above the slider track
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (rawDragOffsetPx < 0f && thresholdCrossed) "Release to Reject" else "← Reject",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (rawDragOffsetPx < 0f && thresholdCrossed) FontWeight.Bold else FontWeight.SemiBold,
                color = if (rawDragOffsetPx < 0f && thresholdCrossed) Color.White else CallEndRed
            )
            Text(
                text = if (rawDragOffsetPx > 0f && thresholdCrossed) "Release to Answer" else "Answer →",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (rawDragOffsetPx > 0f && thresholdCrossed) FontWeight.Bold else FontWeight.SemiBold,
                color = if (rawDragOffsetPx > 0f && thresholdCrossed) Color.White else CallAcceptGreen
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .semantics {
                    contentDescription = "Incoming call slider. Slide right to answer or slide left to reject."
                    customActions = listOf(
                        CustomAccessibilityAction("Answer call") {
                            if (!actionTriggered) {
                                actionTriggered = true
                                onAnswer()
                            }
                            true
                        },
                        CustomAccessibilityAction("Reject call") {
                            if (!actionTriggered) {
                                actionTriggered = true
                                onReject()
                            }
                            true
                        }
                    )
                }
                .testTag("incoming_call_slide_track"),
            contentAlignment = Alignment.Center
        ) {
            val trackWidthPx = with(density) { maxWidth.toPx() }
            val thumbSizePx = with(density) { thumbSize.toPx() }
            val padPx = with(density) { horizontalTrackPadding.toPx() }
            val maxTravelPx = ((trackWidthPx - thumbSizePx - padPx * 2f) / 2f).coerceAtLeast(1f)

            val progress = (animatedOffsetPx / maxTravelPx).coerceIn(-1f, 1f)
            val answerProgress = progress.coerceIn(0f, 1f)
            val rejectProgress = (-progress).coerceIn(0f, 1f)

            val trackBorderColor = when {
                answerProgress > 0.05f -> lerp(Color(0xFF283A56), CallAcceptGreen, answerProgress)
                rejectProgress > 0.05f -> lerp(Color(0xFF283A56), CallEndRed, rejectProgress)
                else -> Color(0xFF2E4260)
            }
            val trackTintColor = when {
                answerProgress > 0.05f -> lerp(Color(0xD90E1726), CallAcceptGreen.copy(alpha = 0.28f), answerProgress)
                rejectProgress > 0.05f -> lerp(Color(0xD90E1726), CallEndRed.copy(alpha = 0.28f), rejectProgress)
                else -> Color(0xD90E1726)
            }

            val thumbColor by animateColorAsState(
                targetValue = when {
                    progress >= 0.14f -> CallAcceptGreen
                    progress <= -0.14f -> CallEndRed
                    else -> SimAccentEmerald
                },
                animationSpec = tween(160),
                label = "slide_thumb_color"
            )

            // Pill track
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = trackTintColor,
                border = BorderStroke(1.5.dp, trackBorderColor),
                modifier = Modifier.fillMaxSize()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Reject Target + Animated Chevrons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .alpha((1f - answerProgress * 1.1f).coerceIn(0.2f, 1f))
                            .scale(1f + 0.08f * rejectProgress)
                            .testTag("incoming_slide_reject_zone")
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CallEndRed.copy(alpha = 0.22f + 0.50f * rejectProgress),
                            border = BorderStroke(1.dp, CallEndRed.copy(alpha = 0.70f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.CallEnd,
                                    contentDescription = "Slide left to Reject",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = CallEndRed.copy(alpha = if (isDragging) 0.95f else chevronWave),
                            modifier = Modifier.size(20.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = CallEndRed.copy(alpha = if (isDragging) 0.65f else (1.15f - chevronWave).coerceIn(0.25f, 0.9f)),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Right Answer Target + Animated Chevrons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .alpha((1f - rejectProgress * 1.1f).coerceIn(0.2f, 1f))
                            .scale(1f + 0.08f * answerProgress)
                            .testTag("incoming_slide_answer_zone")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = CallAcceptGreen.copy(alpha = if (isDragging) 0.65f else (1.15f - chevronWave).coerceIn(0.25f, 0.9f)),
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = CallAcceptGreen.copy(alpha = if (isDragging) 0.95f else chevronWave),
                            modifier = Modifier.size(20.dp)
                        )
                        Surface(
                            shape = CircleShape,
                            color = CallAcceptGreen.copy(alpha = 0.22f + 0.50f * answerProgress),
                            border = BorderStroke(1.dp, CallAcceptGreen.copy(alpha = 0.70f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Call,
                                    contentDescription = "Slide right to Answer",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Center Draggable Handle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                    .size(thumbSize)
                    .pointerInput(maxTravelPx) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                if (!actionTriggered) {
                                    isDragging = true
                                    thresholdCrossed = false
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (actionTriggered) return@detectHorizontalDragGestures
                                change.consume()
                                val next = (rawDragOffsetPx + dragAmount).coerceIn(-maxTravelPx, maxTravelPx)
                                rawDragOffsetPx = next
                                val ratio = abs(next) / maxTravelPx
                                if (ratio >= commitThreshold && !thresholdCrossed) {
                                    thresholdCrossed = true
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } else if (ratio < commitThreshold - 0.1f && thresholdCrossed) {
                                    thresholdCrossed = false
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                                if (actionTriggered) return@detectHorizontalDragGestures
                                val finalRatio = rawDragOffsetPx / maxTravelPx
                                when {
                                    finalRatio >= commitThreshold -> {
                                        actionTriggered = true
                                        rawDragOffsetPx = maxTravelPx
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onAnswer()
                                    }
                                    finalRatio <= -commitThreshold -> {
                                        actionTriggered = true
                                        rawDragOffsetPx = -maxTravelPx
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onReject()
                                    }
                                    else -> {
                                        rawDragOffsetPx = 0f
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                if (!actionTriggered) {
                                    rawDragOffsetPx = 0f
                                }
                            }
                        )
                    }
                    .testTag("incoming_call_slide_thumb")
            ) {
                // Idle breathing halo around handle
                if (!isDragging && !actionTriggered) {
                    Box(
                        modifier = Modifier
                            .size(thumbSize)
                            .scale(thumbHaloScale)
                            .clip(CircleShape)
                            .background(thumbColor.copy(alpha = 0.24f))
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    shadowElevation = 8.dp,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(thumbSize)
                        .scale(1f + 0.06f * abs(progress))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    thumbColor,
                                    thumbColor.copy(alpha = 0.85f)
                                )
                            )
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (progress <= -0.14f) Icons.Filled.CallEnd else Icons.Filled.Call,
                            contentDescription = "Drag right to Answer or left to Reject",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = "Slide to Answer or Reject",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF94A3B8),
            modifier = Modifier.testTag("incoming_call_slide_hint")
        )
    }
}
