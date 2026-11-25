package com.twintitanium.gifttracker.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Reusable drag-and-drop reorder state for a [LazyColumn] section.
 *
 * @param lazyListState The [LazyListState] attached to the [LazyColumn].
 * @param headerCount Number of static/non-reorderable items before the reorderable items.
 * @param itemCount Current number of reorderable items.
 * @param onMove Called during drag when an item crosses into another item's slot.
 * @param onDragEnd Called once when the drag gesture finishes or is cancelled.
 */
class DragDropState(
    val lazyListState: LazyListState,
    private val headerCount: () -> Int,
    private val itemCount: () -> Int,
    private val onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    private val onDragEnd: () -> Unit
) {
    var draggingItemKey by mutableStateOf<Any?>(null)
        private set

    var draggingItemOffset by mutableFloatStateOf(0f)
        private set

    internal var currentOverscroll by mutableFloatStateOf(0f)
        private set

    private val currentItemInfo: LazyListItemInfo?
        get() = draggingItemKey?.let { key ->
            lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
        }

    fun onDragStart(key: Any) {
        draggingItemKey = key
        draggingItemOffset = 0f
        currentOverscroll = 0f
    }

    fun onDragInterrupted() {
        if (draggingItemKey != null) {
            draggingItemKey = null
            draggingItemOffset = 0f
            currentOverscroll = 0f
            onDragEnd()
        }
    }

    fun onDrag(dragAmountY: Float) {
        val current = currentItemInfo ?: return
        draggingItemOffset += dragAmountY

        val startOffset = current.offset + draggingItemOffset
        val endOffset = startOffset + current.size
        val middleOffset = startOffset + current.size / 2f

        val headers = headerCount()
        val count = itemCount()
        val validRange = headers until (headers + count)

        val target = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index in validRange &&
                item.index != current.index &&
                middleOffset >= item.offset &&
                middleOffset <= item.offset + item.size
        }

        if (target != null) {
            val fromIndex = current.index - headers
            val toIndex = target.index - headers
            if (fromIndex in 0 until count && toIndex in 0 until count) {
                val offsetDelta = if (target.index > current.index) {
                    (target.offset + target.size) - (current.offset + current.size)
                } else {
                    -(current.offset - target.offset)
                }
                draggingItemOffset -= offsetDelta
                onMove(fromIndex, toIndex)
            }
        }

        // Auto-scroll when dragged near the top or bottom of the viewport
        val viewportStart = lazyListState.layoutInfo.viewportStartOffset.toFloat()
        val viewportEnd = lazyListState.layoutInfo.viewportEndOffset.toFloat()
        currentOverscroll = when {
            startOffset < viewportStart + 48f -> (startOffset - (viewportStart + 48f)).coerceAtLeast(-24f)
            endOffset > viewportEnd - 48f -> (endOffset - (viewportEnd - 48f)).coerceAtMost(24f)
            else -> 0f
        }
    }
}

@Composable
fun rememberDragDropState(
    lazyListState: LazyListState,
    headerCount: Int,
    itemCount: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onDragEnd: () -> Unit
): DragDropState {
    val latestHeaderCount by rememberUpdatedState(headerCount)
    val latestItemCount by rememberUpdatedState(itemCount)
    val latestOnMove by rememberUpdatedState(onMove)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    val state = remember(lazyListState) {
        DragDropState(
            lazyListState = lazyListState,
            headerCount = { latestHeaderCount },
            itemCount = { latestItemCount },
            onMove = { from, to -> latestOnMove(from, to) },
            onDragEnd = { latestOnDragEnd() }
        )
    }

    LaunchedEffect(state, state.draggingItemKey, state.currentOverscroll) {
        while (isActive && state.draggingItemKey != null && state.currentOverscroll != 0f) {
            val consumed = lazyListState.scrollBy(state.currentOverscroll)
            if (consumed != 0f) {
                state.onDrag(consumed)
            } else {
                break
            }
            delay(16)
        }
    }

    return state
}

/**
 * Applies elevation, z-index, and translationY to the currently dragged item,
 * and smooth placement animation to all other items.
 */
fun LazyItemScope.draggableItemModifier(
    state: DragDropState,
    key: Any
): Modifier {
    val isDragging = state.draggingItemKey == key
    return if (isDragging) {
        Modifier
            .zIndex(1f)
            .graphicsLayer {
                translationY = state.draggingItemOffset
                scaleX = 1.02f
                scaleY = 1.02f
                shadowElevation = 12f
            }
    } else {
        Modifier.animateItem()
    }
}

/**
 * Attaches immediate drag-to-reorder gesture handling to a dedicated drag handle.
 */
fun Modifier.dragHandle(
    state: DragDropState,
    key: Any,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    val haptic = LocalHapticFeedback.current
    val currentKey by rememberUpdatedState(key)
    this
        .pointerGrabCursor(isDragging = state.draggingItemKey == currentKey)
        .pointerInput(state, currentKey) {
            detectDragGestures(
                onDragStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.onDragStart(currentKey)
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    state.onDrag(dragAmount.y)
                },
                onDragEnd = { state.onDragInterrupted() },
                onDragCancel = { state.onDragInterrupted() }
            )
        }
}

/**
 * Attaches long-press-then-drag reorder gesture handling to an entire item container.
 */
fun Modifier.longPressDraggable(
    state: DragDropState,
    key: Any,
    enabled: Boolean = true
): Modifier = composed {
    if (!enabled) return@composed this
    val haptic = LocalHapticFeedback.current
    val currentKey by rememberUpdatedState(key)
    pointerInput(state, currentKey) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                state.onDragStart(currentKey)
            },
            onDrag = { change, dragAmount ->
                change.consume()
                state.onDrag(dragAmount.y)
            },
            onDragEnd = { state.onDragInterrupted() },
            onDragCancel = { state.onDragInterrupted() }
        )
    }
}
