package com.twintitanium.gifttracker.ui.components

import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext

/**
 * Displays a pointing-hand cursor when hovering with a mouse or trackpad.
 */
fun Modifier.pointerHandCursor(): Modifier = pointerHoverIcon(PointerIcon.Hand)

/**
 * Displays an open/closed grab cursor on desktop/trackpad drag handles.
 */
fun Modifier.pointerGrabCursor(isDragging: Boolean = false): Modifier = composed {
    val context = LocalContext.current
    val icon = remember(context, isDragging) {
        val type = if (isDragging) {
            android.view.PointerIcon.TYPE_GRABBING
        } else {
            android.view.PointerIcon.TYPE_GRAB
        }
        PointerIcon(android.view.PointerIcon.getSystemIcon(context, type))
    }
    pointerHoverIcon(icon)
}

/**
 * Invokes [onClick] when the user secondary-clicks (right-clicks) with a mouse or trackpad.
 */
fun Modifier.onSecondaryClick(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                event.changes.forEach { it.consume() }
                onClick()
            }
        }
    }
}
