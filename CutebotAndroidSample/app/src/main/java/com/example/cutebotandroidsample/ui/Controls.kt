package com.example.cutebotandroidsample.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.cutebotandroidsample.ui.theme.CarbonLine
import com.example.cutebotandroidsample.ui.theme.SignalGreen
import com.example.cutebotandroidsample.ui.theme.TrackCyan
import com.example.cutebotandroidsample.ui.theme.TrackMagenta
import com.example.cutebotandroidsample.ui.theme.TrackPurple
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Analog thumb pad producing a throttle/steer pair.
 *
 * Touch is tracked by pointer id rather than by gesture detector so the knob follows a
 * finger that starts anywhere on the pad and never loses it to a competing gesture — a
 * dropped pointer mid-corner means a car that keeps its last command into a barrier.
 * Releasing always emits a true zero before the knob springs home.
 */
@Composable
fun JoystickPad(
    onInput: (throttle: Float, steer: Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 230.dp,
    enabled: Boolean = true,
) {
    val radiusPx = with(LocalDensity.current) { diameter.toPx() / 2f }
    val knobRadius = radiusPx * 0.30f
    val travel = radiusPx - knobRadius

    val knob = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onInput)

    Box(
        modifier = modifier
            .size(diameter)
            .pointerInput(enabled, travel) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val centre = Offset(size.width / 2f, size.height / 2f)

                    fun publish(position: Offset) {
                        val raw = position - centre
                        val distance = raw.getDistance()
                        val clamped = if (distance > travel && distance > 0f) {
                            raw * (travel / distance)
                        } else {
                            raw
                        }
                        scope.launch { knob.snapTo(clamped) }
                        // Screen Y grows downward; throttle grows upward.
                        callback(-(clamped.y / travel), clamped.x / travel)
                    }

                    publish(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        publish(change.position)
                    }

                    callback(0f, 0f)
                    scope.launch {
                        knob.animateTo(
                            Offset.Zero,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        )
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centre = Offset(this.size.width / 2f, this.size.height / 2f)
            val position = knob.value
            val deflection = (position.getDistance() / travel).coerceIn(0f, 1f)

            // Well
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1A26), Color(0xFF0C0C12)),
                    center = centre,
                    radius = radiusPx,
                ),
                radius = radiusPx,
                center = centre,
            )
            drawCircle(
                color = CarbonLine,
                radius = radiusPx - 1f,
                center = centre,
                style = Stroke(width = 2f),
            )

            // Axis crosshair, fading in as the stick is deflected.
            val hairAlpha = 0.12f + deflection * 0.2f
            drawLine(
                color = Color.White.copy(alpha = hairAlpha),
                start = Offset(centre.x - radiusPx * 0.6f, centre.y),
                end = Offset(centre.x + radiusPx * 0.6f, centre.y),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color.White.copy(alpha = hairAlpha),
                start = Offset(centre.x, centre.y - radiusPx * 0.6f),
                end = Offset(centre.x, centre.y + radiusPx * 0.6f),
                strokeWidth = 1.5f,
            )

            // Deflection trail from centre to knob.
            if (deflection > 0.02f) {
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(TrackPurple.copy(alpha = 0f), TrackMagenta),
                        start = centre,
                        end = centre + position,
                    ),
                    start = centre,
                    end = centre + position,
                    strokeWidth = knobRadius * 0.5f,
                )
            }

            // Knob glow scales with how hard the stick is pushed.
            val knobCentre = centre + position
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(TrackMagenta.copy(alpha = 0.35f * deflection), Color.Transparent),
                    center = knobCentre,
                    radius = knobRadius * 2.2f,
                ),
                radius = knobRadius * 2.2f,
                center = knobCentre,
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(TrackMagenta, TrackPurple),
                    start = knobCentre - Offset(knobRadius, knobRadius),
                    end = knobCentre + Offset(knobRadius, knobRadius),
                ),
                radius = knobRadius,
                center = knobCentre,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = knobRadius,
                center = knobCentre,
                style = Stroke(width = 2f),
            )
        }
    }
}

/**
 * Vertical throttle trigger used in tilt-steering mode, where the thumb supplies only
 * forward/backward and the phone's roll does the steering. Centre-sprung like the pad.
 */
@Composable
fun ThrottleTrigger(
    onThrottle: (Float) -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 84.dp,
    height: Dp = 230.dp,
    enabled: Boolean = true,
) {
    val value = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val callback by rememberUpdatedState(onThrottle)

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val halfHeight = size.height / 2f

                    fun publish(position: Offset) {
                        val normalised = (-(position.y - halfHeight) / halfHeight).coerceIn(-1f, 1f)
                        scope.launch { value.snapTo(normalised) }
                        callback(normalised)
                    }

                    publish(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        publish(change.position)
                    }

                    callback(0f)
                    scope.launch { value.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val corner = w / 2f

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(Color(0xFF1A1A26), Color(0xFF0C0C12))),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
            )
            drawRoundRect(
                color = CarbonLine,
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                style = Stroke(width = 2f),
            )

            // Neutral datum.
            drawLine(
                color = Color.White.copy(alpha = 0.18f),
                start = Offset(w * 0.2f, h / 2f),
                end = Offset(w * 0.8f, h / 2f),
                strokeWidth = 2f,
            )

            // Fill from the datum toward the current demand: green forward, cyan reverse.
            val v = value.value
            if (abs(v) > 0.02f) {
                val top = if (v > 0) h / 2f - (h / 2f) * v else h / 2f
                val extent = (h / 2f) * abs(v)
                drawRoundRect(
                    color = if (v > 0) SignalGreen.copy(alpha = 0.75f) else TrackCyan.copy(alpha = 0.7f),
                    topLeft = Offset(w * 0.18f, top),
                    size = Size(w * 0.64f, extent),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner / 2f, corner / 2f),
                )
            }

            // Thumb.
            val thumbY = (h / 2f) - (h / 2f) * v
            drawCircle(
                brush = Brush.linearGradient(listOf(TrackMagenta, TrackPurple)),
                radius = w * 0.34f,
                center = Offset(w / 2f, thumbY),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = w * 0.34f,
                center = Offset(w / 2f, thumbY),
                style = Stroke(width = 2f),
            )
        }
    }
}
