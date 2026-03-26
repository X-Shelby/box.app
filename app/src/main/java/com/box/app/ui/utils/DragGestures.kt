package com.box.app.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope

suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (PointerInputChange) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    consumeChanges: Boolean = true,
    onDrag: (change: PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onDragStart(down)
        var dragCancelled = false
        val success = drag(down.id) { change ->
            onDrag(change, change.position - change.previousPosition)
            if (consumeChanges) {
                change.consume()
            }
        }
        if (success) {
            onDragEnd()
        } else {
            onDragCancel()
        }
    }
}
