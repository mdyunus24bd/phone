package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCell
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.telecom.SpamRiskLevel
import com.example.ui.theme.CallEndRed
import com.example.ui.theme.EmeraldPrimaryLight
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.QualityExcellent
import com.example.ui.theme.QualityFair
import com.example.ui.theme.QualityGood
import com.example.ui.theme.QualityPoor
import com.example.ui.theme.SimAccentEmerald
import com.example.ui.theme.SpamWarningCoral
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NetworkBadge(
    networkType: String = "SIM",
    modifier: Modifier = Modifier
) {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val accent = if (isLight) EmeraldPrimaryLight else SimAccentEmerald
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = if (isLight) 0.12f else 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.NetworkCell,
                contentDescription = "SIM Cellular route",
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "SIM",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EncryptionChip(
    label: String = "AES-256-GCM",
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "Encrypted",
                tint = primary,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = primary
            )
        }
    }
}

@Composable
fun MosBadge(
    mosScore: Float,
    modifier: Modifier = Modifier
) {
    if (mosScore <= 0f) return
    val color = mosQualityColor(mosScore)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text = "MOS ${"%.1f".format(mosScore)}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun SpamRiskChip(
    level: SpamRiskLevel,
    score: Int,
    modifier: Modifier = Modifier
) {
    val color = when (level) {
        SpamRiskLevel.SAFE -> QualityExcellent
        SpamRiskLevel.CAUTION -> QualityFair
        SpamRiskLevel.HIGH_RISK -> SpamWarningCoral
        SpamRiskLevel.BLOCKED -> CallEndRed
    }
    val icon = when (level) {
        SpamRiskLevel.SAFE -> Icons.Filled.Shield
        else -> Icons.Filled.Warning
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = level.label,
                tint = color,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "${level.label} ($score)",
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun ContactAvatar(
    name: String,
    colorIndex: Int,
    photoUri: String = "",
    size: Dp = 44.dp
) {
    val context = LocalContext.current
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val palette = if (isLight) {
        listOf(
            Color(0xFF007B63),
            Color(0xFF1565C0),
            Color(0xFF5E35B1),
            Color(0xFFB26A00),
            Color(0xFFBE123C)
        )
    } else {
        listOf(
            Color(0xFF00D4AA),
            Color(0xFF38BDF8),
            Color(0xFFA78BFA),
            Color(0xFFF59E0B),
            Color(0xFFFB7185)
        )
    }
    val accent = palette[kotlin.math.abs(colorIndex) % palette.size]
    val initials = name.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "#" }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.10f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = if (size >= 64.dp) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = accent,
            fontWeight = FontWeight.Bold
        )
        if (photoUri.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(photoUri)
                    .crossfade(true)
                    .build(),
                contentDescription = if (name.isNotBlank()) "$name photo" else "Contact photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
fun QualityTrendChart(
    values: List<Float>,
    lineColor: Color,
    minVal: Float = 1.0f,
    maxVal: Float = 4.5f,
    modifier: Modifier = Modifier
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Subtle horizontal reference lines
        for (i in 0..3) {
            val y = h * (i / 3f)
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        if (values.isEmpty()) return@Canvas

        val range = (maxVal - minVal).coerceAtLeast(0.1f)
        val stepX = if (values.size > 1) w / (values.size - 1).toFloat() else w

        val linePath = Path()
        val fillPath = Path()

        values.forEachIndexed { index, raw ->
            val clamped = raw.coerceIn(minVal, maxVal)
            val normY = 1f - ((clamped - minVal) / range)
            val x = if (values.size == 1) w / 2f else index * stepX
            val y = (normY * (h - 16f)) + 8f
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
            if (index == values.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        if (values.size > 1) {
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent)
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // Plot latest point dot
        val lastVal = values.last().coerceIn(minVal, maxVal)
        val lastX = if (values.size == 1) w / 2f else w
        val lastY = ((1f - ((lastVal - minVal) / range)) * (h - 16f)) + 8f
        drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = Offset(lastX, lastY))
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MonoMetricStyle.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }
}

fun mosQualityColor(mos: Float): Color = when {
    mos >= 4.0f -> QualityExcellent
    mos >= 3.4f -> QualityGood
    mos >= 2.6f -> QualityFair
    else -> QualityPoor
}

fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins >= 60) {
        val hrs = mins / 60
        val remMins = mins % 60
        "%d:%02d:%02d".format(hrs, remMins, secs)
    } else {
        "%02d:%02d".format(mins, secs)
    }
}

fun formatTimestamp(ts: Long): String {
    if (ts <= 0L) return "Never"
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return fmt.format(Date(ts))
}
