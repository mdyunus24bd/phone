package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class DialKey(val main: String, val letters: String)

internal val DialKeys = listOf(
    listOf(DialKey("1", "∞"), DialKey("2", "ABC"), DialKey("3", "DEF")),
    listOf(DialKey("4", "GHI"), DialKey("5", "JKL"), DialKey("6", "MNO")),
    listOf(DialKey("7", "PQRS"), DialKey("8", "TUV"), DialKey("9", "WXYZ")),
    listOf(DialKey("*", "+"), DialKey("0", "+"), DialKey("#", ""))
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CircularModernKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val keyDiameter = (68f * sizeScale).dp.coerceIn(54.dp, 86.dp)
    val rowSpacing = (10f * sizeScale).dp.coerceIn(6.dp, 14.dp)
    val digitFontSize = (26f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("keypad_circular_modern"),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DialKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isSymbol = key.main == "*" || key.main == "#"
                    Surface(
                        shape = CircleShape,
                        color = if (isSymbol) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                        ),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .size(keyDiameter)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onKeyPress(key.main.first()) },
                                onLongClick = { if (key.main == "0") onZeroLongPress() }
                            )
                            .testTag("dial_key_${key.main}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = key.main,
                                fontSize = digitFontSize,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    fontSize = lettersFontSize,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CompactTactileKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val keyHeight = (50f * sizeScale).dp.coerceIn(48.dp, 70.dp)
    val spacing = (8f * sizeScale).dp.coerceIn(6.dp, 12.dp)
    val digitFontSize = (22f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keypad_compact_tactile")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            DialKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    row.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .height(keyHeight)
                                .clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = { onKeyPress(key.main.first()) },
                                    onLongClick = { if (key.main == "0") onZeroLongPress() }
                                )
                                .testTag("dial_key_${key.main}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = key.main,
                                    fontSize = digitFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (key.letters.isNotEmpty()) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = key.letters,
                                        fontSize = lettersFontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OneHandedKeypad(
    alignRight: Boolean,
    sizeScale: Float = 1.0f,
    dialInputNotEmpty: Boolean,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit,
    onSwapHand: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit
) {
    val keyHeight = (54f * sizeScale).dp.coerceIn(48.dp, 74.dp)
    val railButtonSize = (54f * sizeScale).dp.coerceIn(48.dp, 66.dp)
    val railWidth = (railButtonSize + 8.dp).coerceIn(56.dp, 74.dp)
    val spacing = (8f * sizeScale).dp.coerceIn(6.dp, 12.dp)
    val digitFontSize = (22f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp

    val sideRail: @Composable () -> Unit = {
        Column(
            modifier = Modifier.width(railWidth),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier
                    .size(railButtonSize)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onSwapHand)
                    .testTag("one_handed_swap_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.SwapHoriz,
                        contentDescription = "Switch keypad hand side",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(railButtonSize)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = dialInputNotEmpty, onClick = onBackspace)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete digit",
                        tint = if (dialInputNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(railButtonSize)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(enabled = dialInputNotEmpty, onClick = onClear)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear number",
                        tint = if (dialInputNotEmpty) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        }
                    )
                }
            }
        }
    }

    val keyCluster: @Composable (Modifier) -> Unit = { clusterModifier ->
        Column(
            modifier = clusterModifier,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            DialKeys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    row.forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(keyHeight)
                                .clip(RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onKeyPress(key.main.first()) },
                                    onLongClick = { if (key.main == "0") onZeroLongPress() }
                                )
                                .testTag("dial_key_${key.main}")
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = key.main,
                                    fontSize = digitFontSize,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (key.letters.isNotEmpty()) {
                                    Text(
                                        text = key.letters,
                                        fontSize = lettersFontSize,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(if (alignRight) "keypad_one_handed_right" else "keypad_one_handed_left"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alignRight) {
            sideRail()
            keyCluster(Modifier.weight(1f))
        } else {
            keyCluster(Modifier.weight(1f))
            sideRail()
        }
    }
}

/**
 * Minimal Clean keypad: borderless floating digits with subtle hairline horizontal dividers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MinimalCleanKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val rowHeight = (58f * sizeScale).dp.coerceIn(48.dp, 76.dp)
    val digitFontSize = (28f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("keypad_minimal_clean")
    ) {
        DialKeys.forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(rowHeight)
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(
                                onClick = { onKeyPress(key.main.first()) },
                                onLongClick = { if (key.main == "0") onZeroLongPress() }
                            )
                            .testTag("dial_key_${key.main}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = key.main,
                                fontSize = digitFontSize,
                                fontWeight = FontWeight.Light,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    fontSize = lettersFontSize,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (rowIndex < DialKeys.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}

/**
 * Accent Ring keypad: crisp outlined circular rings with subtle accent glow and bold numerals.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AccentRingKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val keyDiameter = (68f * sizeScale).dp.coerceIn(54.dp, 86.dp)
    val rowSpacing = (10f * sizeScale).dp.coerceIn(6.dp, 14.dp)
    val digitFontSize = (26f * sizeScale).sp
    val lettersFontSize = (9.5f * sizeScale).sp
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("keypad_accent_ring"),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DialKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    val isSymbol = key.main == "*" || key.main == "#"
                    Surface(
                        shape = CircleShape,
                        color = if (isSymbol) {
                            secondary.copy(alpha = 0.08f)
                        } else {
                            primary.copy(alpha = 0.08f)
                        },
                        border = BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                listOf(
                                    primary.copy(alpha = if (isSymbol) 0.45f else 0.75f),
                                    secondary.copy(alpha = 0.55f)
                                )
                            )
                        ),
                        modifier = Modifier
                            .size(keyDiameter)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { onKeyPress(key.main.first()) },
                                onLongClick = { if (key.main == "0") onZeroLongPress() }
                            )
                            .testTag("dial_key_${key.main}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = key.main,
                                fontSize = digitFontSize,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    fontSize = lettersFontSize,
                                    fontWeight = FontWeight.Medium,
                                    color = primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Soft Capsule keypad: wide ergonomic pill keys with tonal primary container highlights.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SoftCapsuleKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val keyHeight = (54f * sizeScale).dp.coerceIn(48.dp, 74.dp)
    val spacing = (9f * sizeScale).dp.coerceIn(6.dp, 13.dp)
    val digitFontSize = (24f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .testTag("keypad_soft_capsule"),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        DialKeys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                row.forEach { key ->
                    val isSymbol = key.main == "*" || key.main == "#"
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isSymbol) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(keyHeight)
                            .clip(RoundedCornerShape(50))
                            .combinedClickable(
                                onClick = { onKeyPress(key.main.first()) },
                                onLongClick = { if (key.main == "0") onZeroLongPress() }
                            )
                            .testTag("dial_key_${key.main}")
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = key.main,
                                fontSize = digitFontSize,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (key.letters.isNotEmpty()) {
                                Text(
                                    text = key.letters,
                                    fontSize = lettersFontSize,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Classic Phone Board keypad: framed 3x4 board with crisp inner grid dividers and tactile keycaps.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ClassicBoardKeypad(
    sizeScale: Float = 1.0f,
    onKeyPress: (Char) -> Unit,
    onZeroLongPress: () -> Unit
) {
    val keyHeight = (54f * sizeScale).dp.coerceIn(48.dp, 74.dp)
    val digitFontSize = (25f * sizeScale).sp
    val lettersFontSize = (10f * sizeScale).sp
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.2.dp, dividerColor),
        tonalElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("keypad_classic_board")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            DialKeys.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(keyHeight),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { colIndex, key ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(
                                    if (key.main == "*" || key.main == "#") {
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .combinedClickable(
                                    onClick = { onKeyPress(key.main.first()) },
                                    onLongClick = { if (key.main == "0") onZeroLongPress() }
                                )
                                .testTag("dial_key_${key.main}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = key.main,
                                    fontSize = digitFontSize,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (key.letters.isNotEmpty()) {
                                    Text(
                                        text = key.letters,
                                        fontSize = lettersFontSize,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (colIndex < row.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxSize()
                                    .background(dividerColor)
                            )
                        }
                    }
                }
                if (rowIndex < DialKeys.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = dividerColor)
                }
            }
        }
    }
}
