package com.example.cutebotandroidsample.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cutebotandroidsample.race.SensorPair
import com.example.cutebotandroidsample.race.TrackMode
import com.example.cutebotandroidsample.ui.theme.CarbonLine
import com.example.cutebotandroidsample.ui.theme.CarbonRaised
import com.example.cutebotandroidsample.ui.theme.SignalAmber
import com.example.cutebotandroidsample.ui.theme.SignalGreen
import com.example.cutebotandroidsample.ui.theme.SignalRed
import com.example.cutebotandroidsample.ui.theme.TextMuted
import com.example.cutebotandroidsample.ui.theme.TrackCyan

/** Raised panel used for every grouped block on the dashboard. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonRaised)
            .border(1.dp, CarbonLine, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content,
    )
}

@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        modifier = modifier,
    )
}

/** Compact readout: a caption, a monospaced value and an optional unit. */
@Composable
fun TelemetryTile(
    label: String,
    value: String,
    unit: String? = null,
    accent: Color = Color.White,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        PanelLabel(label)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = accent,
            )
            if (unit != null) {
                Text(
                    text = " $unit",
                    fontSize = 10.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

/**
 * Visualises both infrared sensors as the car sees them.
 *
 * On the Robot Rally mat dark means "on the racing surface" and white means "touching a
 * boundary", so a lit pad is a warning rather than a success — the colours are chosen to
 * read that way at a glance, and flip when the classic line-following mode is selected.
 */
@Composable
fun LineSensorView(
    code: Int?,
    trackMode: TrackMode,
    modifier: Modifier = Modifier,
) {
    val reading = code?.let { SensorPair.from(it) }
    val leftOnWhite = reading == SensorPair.RIGHT_DARK || reading == SensorPair.BOTH_WHITE
    val rightOnWhite = reading == SensorPair.LEFT_DARK || reading == SensorPair.BOTH_WHITE

    // In edge-avoid a white reading is a boundary (bad); in line-follow it is off-line.
    val alertColor = if (trackMode == TrackMode.EDGE_AVOID) SignalAmber else TrackCyan

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SensorPad(label = "L", triggered = leftOnWhite, unknown = reading == null, alertColor = alertColor)
        SensorPad(label = "R", triggered = rightOnWhite, unknown = reading == null, alertColor = alertColor)
    }
}

@Composable
private fun SensorPad(label: String, triggered: Boolean, unknown: Boolean, alertColor: Color) {
    val target = when {
        unknown -> CarbonLine
        triggered -> alertColor
        else -> SignalGreen.copy(alpha = 0.22f)
    }
    val color by animateColorAsState(target, tween(120), label = "sensorPad")
    Box(
        modifier = Modifier
            .size(width = 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .border(1.dp, if (triggered) alertColor else CarbonLine, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (triggered) Color.Black else TextMuted,
        )
    }
}

/**
 * Ultrasonic proximity bar. The mat is scattered with cones, so the scale is compressed
 * into the range that actually matters for avoiding one: 0-60cm.
 */
@Composable
fun ProximityGauge(cm: Int?, modifier: Modifier = Modifier) {
    val fraction = when {
        cm == null || cm <= 0 -> 0f
        else -> (1f - (cm.coerceAtMost(60) / 60f))
    }
    val animated by animateFloatAsState(fraction, tween(140), label = "proximity")
    val accent = when {
        cm == null -> CarbonLine
        cm <= 9 -> SignalRed
        cm <= 18 -> SignalAmber
        else -> SignalGreen
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PanelLabel("Obstacle")
            Text(
                text = cm?.let { "$it cm" } ?: "—",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.06f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.5f), accent))),
            )
        }
    }
}

/** Four-bar signal meter for the measured PING round trip. */
@Composable
fun LatencyMeter(latencyMs: Long?, stale: Boolean, modifier: Modifier = Modifier) {
    val bars = when {
        stale || latencyMs == null -> 0
        latencyMs < 60 -> 4
        latencyMs < 120 -> 3
        latencyMs < 220 -> 2
        else -> 1
    }
    val accent = when (bars) {
        4, 3 -> SignalGreen
        2 -> SignalAmber
        1 -> SignalRed
        else -> CarbonLine
    }

    Row(modifier = modifier, verticalAlignment = Alignment.Bottom) {
        Canvas(Modifier.size(width = 26.dp, height = 16.dp)) {
            val barWidth = size.width / 7f
            repeat(4) { index ->
                val heightFraction = 0.3f + index * 0.233f
                val barHeight = size.height * heightFraction
                drawRect(
                    color = if (index < bars) accent else Color.White.copy(alpha = 0.12f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = index * barWidth * 1.75f,
                        y = size.height - barHeight,
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (stale || latencyMs == null) "—" else "$latencyMs ms",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
    }
}

/** Horizontal bar showing a signed motor demand, anchored at centre: reverse grows left. */
@Composable
fun MotorBar(label: String, speed: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState((speed / 100f).coerceIn(-1f, 1f), tween(90), label = "motor")
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 10.sp, color = TextMuted, modifier = Modifier.width(14.dp))
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(10.dp),
        ) {
            val radius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(color = Color.White.copy(alpha = 0.06f), cornerRadius = radius)

            val centre = size.width / 2f
            val extent = centre * kotlin.math.abs(fraction)
            if (extent > 0.5f) {
                drawRoundRect(
                    color = if (fraction >= 0) SignalGreen else TrackCyan,
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = if (fraction >= 0) centre else centre - extent,
                        y = 0f,
                    ),
                    size = androidx.compose.ui.geometry.Size(extent, size.height),
                    cornerRadius = radius,
                )
            }
            // Neutral datum tick.
            drawLine(
                color = Color.White.copy(alpha = 0.3f),
                start = androidx.compose.ui.geometry.Offset(centre, 0f),
                end = androidx.compose.ui.geometry.Offset(centre, size.height),
                strokeWidth = 1.5f,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = "%4d".format(speed),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.White,
        )
    }
}
