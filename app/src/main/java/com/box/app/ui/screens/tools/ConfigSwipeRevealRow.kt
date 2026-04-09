package com.box.app.ui.screens.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appErrorColor
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun SwipeRevealRow(
    rowKey: String,
    openRowKey: String?,
    onOpenRowKeyChange: (String?) -> Unit,
    closeSignal: Int,
    revealWidth: Dp,
    onRefresh: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val c = appColors()
    val accent = appAccentColor()
    val danger = appErrorColor()
    val interactionSource = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = remember(revealWidth, density) { with(density) { revealWidth.toPx() } }
    val offsetX = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableStateOf(0) }

    LaunchedEffect(closeSignal) {
        if (offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 160))
            onOpenRowKeyChange(null)
        }
    }

    LaunchedEffect(openRowKey) {
        if (openRowKey != rowKey && offsetX.value != 0f) {
            offsetX.animateTo(0f, animationSpec = tween(durationMillis = 160))
        }
    }

    val actionsCount = remember(onRefresh, onEdit, onRename) {
        var n = 1
        if (onRefresh != null) n += 1
        if (onEdit != null) n += 1
        if (onRename != null) n += 1
        n
    }
    val actionWidth = remember(revealWidth, actionsCount) { revealWidth / actionsCount }

    val contentHeightDp = remember(contentHeightPx, density) {
        if (contentHeightPx <= 0) 0.dp else with(density) { contentHeightPx.toDp() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
    ) {
        if (contentHeightDp > 0.dp) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(revealWidth)
                    .height(contentHeightDp)
                    .clip(RoundedRectangle(14.dp)),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onRefresh != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onRefresh()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onEdit != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onEdit()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = stringResource(R.string.tools_config_edit),
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onRename != null) {
                    Box(
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxSize()
                            .background(c.cardAlt)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = {
                                    onRename()
                                    onOpenRowKeyChange(null)
                                    scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.tools_config_rename),
                            tint = c.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(actionWidth)
                        .fillMaxSize()
                        .background(danger.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                onDelete()
                                onOpenRowKeyChange(null)
                                scope.launch { offsetX.animateTo(0f, animationSpec = tween(160)) }
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.tools_config_delete),
                        tint = danger,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .background(c.card)
                .onSizeChanged { contentHeightPx = it.height }
                .pointerInput(revealPx, openRowKey) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val startOffset = offsetX.value
                        val slop = awaitHorizontalTouchSlopOrCancellation(
                            pointerId = down.id,
                            onTouchSlopReached = { change, over ->
                                change.consume()
                                val next = (startOffset + over).coerceIn(-revealPx, 0f)
                                scope.launch { offsetX.snapTo(next) }
                            }
                        )

                        if (slop != null) {
                            horizontalDrag(
                                pointerId = slop.id
                            ) { change ->
                                val dx = change.positionChange().x
                                if (dx != 0f) {
                                    change.consume()
                                    val next = (offsetX.value + dx).coerceIn(-revealPx, 0f)
                                    scope.launch { offsetX.snapTo(next) }
                                }
                            }

                            val shouldOpen = offsetX.value < -revealPx * 0.5f
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = if (shouldOpen) -revealPx else 0f,
                                    animationSpec = tween(durationMillis = 180)
                                )
                            }

                            if (shouldOpen) {
                                onOpenRowKeyChange(rowKey)
                            } else if (openRowKey == rowKey) {
                                onOpenRowKeyChange(null)
                            }
                        }
                    }
                }
        ) {
            content()
        }
    }
}
