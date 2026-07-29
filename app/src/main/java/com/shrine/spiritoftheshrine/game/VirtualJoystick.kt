package com.shrine.spiritoftheshrine.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

private val BASE_RADIUS = 64.dp
private val KNOB_RADIUS = 26.dp

@Composable
fun VirtualJoystick(modifier: Modifier = Modifier, onChange: (dx: Float, dy: Float) -> Unit) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    val view = LocalView.current

    Canvas(
        modifier = modifier
            .size(BASE_RADIUS * 2)
            .excludeFromSystemGestures(view)
            .pointerInput(Unit) {
                val radiusPx = BASE_RADIUS.toPx()
                detectDragGestures(
                    onDragEnd = {
                        knobOffset = Offset.Zero
                        onChange(0f, 0f)
                    },
                    onDragCancel = {
                        knobOffset = Offset.Zero
                        onChange(0f, 0f)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val proposed = knobOffset + dragAmount
                        val distance = hypot(proposed.x, proposed.y)
                        knobOffset = if (distance > radiusPx) proposed * (radiusPx / distance) else proposed
                        onChange(knobOffset.x / radiusPx, knobOffset.y / radiusPx)
                    },
                )
            }
    ) {
        val baseRadiusPx = BASE_RADIUS.toPx()
        val knobRadiusPx = KNOB_RADIUS.toPx()
        drawCircle(color = Color.White.copy(alpha = 0.25f), radius = baseRadiusPx, center = center)
        drawCircle(color = Color.White.copy(alpha = 0.55f), radius = knobRadiusPx, center = center + knobOffset)
    }
}
