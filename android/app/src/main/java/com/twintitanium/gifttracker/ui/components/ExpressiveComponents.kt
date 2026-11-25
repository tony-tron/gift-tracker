package com.twintitanium.gifttracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun avatarPolygonShape(shapeName: String): Shape = when (shapeName) {
    "cookie" -> MaterialShapes.Cookie6Sided.toShape()
    "clover" -> MaterialShapes.Clover4Leaf.toShape()
    "arch" -> MaterialShapes.Arch.toShape()
    "pill" -> MaterialShapes.Pill.toShape()
    "flower" -> MaterialShapes.Flower.toShape()
    else -> MaterialShapes.Square.toShape()
}

fun avatarToneColors(tone: String): Pair<Color, Color> = when (tone) {
    "sage" -> Color(0xFF2B4C33) to Color(0xFFB7F1BA)
    "amber" -> Color(0xFF563D0A) to Color(0xFFFFDEA3)
    "teal" -> Color(0xFF104956) to Color(0xFFA5EEFF)
    "rose" -> Color(0xFF5A2238) to Color(0xFFFFD9E3)
    "ochre" -> Color(0xFF533F14) to Color(0xFFFBE0A6)
    else -> Color(0xFF5E2B1C) to Color(0xFFFFDBD1) // terracotta
}

@Composable
fun ExpressiveAvatar(
    name: String,
    shapeName: String,
    tone: String,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val shape = avatarPolygonShape(shapeName)
    val (bg, fg) = avatarToneColors(tone)
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            color = fg,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.34f).sp
        )
    }
}

@Composable
fun ExpressiveBudgetBar(
    progress: Float,
    isOverBudget: Boolean = false,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "wavyBudgetProgress"
    )

    LinearWavyProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp),
        color = if (isOverBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun GiftStatusConnectedGroup(
    bought: Boolean,
    received: Boolean,
    wrapped: Boolean,
    onToggleBought: () -> Unit,
    onToggleReceived: () -> Unit,
    onToggleWrapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        FilledTonalToggleButton(
            checked = bought,
            onCheckedChange = {
                haptics.performHapticFeedback(
                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                )
                onToggleBought()
            },
            modifier = Modifier.weight(1f),
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
        ) {
            Icon(
                imageVector = if (bought) Icons.Rounded.Check else Icons.Rounded.ShoppingBag,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Bought", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        FilledTonalToggleButton(
            checked = received,
            onCheckedChange = {
                haptics.performHapticFeedback(
                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                )
                onToggleReceived()
            },
            modifier = Modifier.weight(1f),
            shapes = ButtonGroupDefaults.connectedMiddleButtonShapes()
        ) {
            Icon(
                imageVector = if (received) Icons.Rounded.Check else Icons.Rounded.Inventory2,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Arrived", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        FilledTonalToggleButton(
            checked = wrapped,
            onCheckedChange = {
                haptics.performHapticFeedback(
                    if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                )
                onToggleWrapped()
            },
            modifier = Modifier.weight(1f),
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
        ) {
            Icon(
                imageVector = if (wrapped) Icons.Rounded.Check else Icons.Rounded.CardGiftcard,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Wrapped", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MoveIdeaSplitButton(
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    onMoreOptionsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    SplitButtonLayout(
        modifier = modifier,
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onPrimaryClick()
                }
            ) {
                Text(
                    text = primaryLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = false,
                onCheckedChange = { onMoreOptionsClick() }
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Choose another occasion",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    )
}
