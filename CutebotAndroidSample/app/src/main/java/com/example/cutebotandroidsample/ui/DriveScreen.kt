package com.example.cutebotandroidsample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import com.example.cutebotandroidsample.race.AssistPhase
import com.example.cutebotandroidsample.race.ControlMode
import com.example.cutebotandroidsample.race.LinkState
import com.example.cutebotandroidsample.race.RaceUiState
import com.example.cutebotandroidsample.race.formatLapTime
import com.example.cutebotandroidsample.ui.theme.CarbonLine
import com.example.cutebotandroidsample.ui.theme.SignalAmber
import com.example.cutebotandroidsample.ui.theme.SignalGreen
import com.example.cutebotandroidsample.ui.theme.SignalRed
import com.example.cutebotandroidsample.ui.theme.TextMuted
import com.example.cutebotandroidsample.ui.theme.TrackCyan
import com.example.cutebotandroidsample.ui.theme.TrackMagenta
import kotlin.math.sqrt

/**
 * The screen you race from. Controls, live stats and the two things a thumb must reach
 * without looking: the stop button and the horn.
 */
@Composable
fun DriveScreen(
    state: RaceUiState,
    actions: RaceActions,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        DriveHeader(state, onOpenSettings)

        // A dropout mid-run keeps the driver here rather than throwing them back to the
        // connect screen; the banner says what is happening while the link retries.
        AnimatedVisibility(visible = !state.link.isReady) {
            Box(Modifier.padding(top = 8.dp)) { ReconnectBanner(state) }
        }
        AnimatedVisibility(visible = state.link.brownoutSuspected) {
            Box(Modifier.padding(top = 8.dp)) {
                BrownoutBanner(onDismiss = actions.onDismissBrownout)
            }
        }

        Spacer(Modifier.height(10.dp))
        LapPanel(state, actions)
        Spacer(Modifier.height(8.dp))
        TelemetryPanel(state)
        Spacer(Modifier.height(8.dp))
        ControlPanel(state, actions)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun DriveHeader(state: RaceUiState, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = state.teamName.ifBlank { "ROBOT RALLY" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
            )
            ConnectionStatusLine(state.link)
        }

        Column(horizontalAlignment = Alignment.End) {
            LatencyMeter(state.telemetry.latencyMs, state.link.stale)
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${state.txPerSecond} tx/s",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = TextMuted,
            )
        }

        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ReconnectBanner(state: RaceUiState) {
    val failed = state.link.state == LinkState.FAILED
    val accent = if (failed) SignalRed else SignalAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.14f))
            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.link.detail,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// --------------------------------------------------------------------- lap panel

@Composable
private fun LapPanel(state: RaceUiState, actions: RaceActions) {
    val lap = state.lap
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PanelLabel(if (lap.running) "Current lap" else "Session")
                Text(
                    text = formatLapTime(if (lap.running) lap.currentLapMs else lap.elapsedMs),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = if (lap.running) Color.White else TextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                PanelLabel("Lap")
                Text(
                    text = "${lap.laps.size}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = TrackMagenta,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatLabel("BEST", lap.bestLapMs?.let(::formatLapTime) ?: "—", SignalGreen)
            StatLabel("LAST", lap.lastLapMs?.let(::formatLapTime) ?: "—", Color.White)
            StatLabel("TOTAL", formatLapTime(lap.elapsedMs), TextMuted)
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = actions.onToggleTimer,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (lap.running) SignalRed.copy(alpha = 0.85f) else SignalGreen,
                    contentColor = Color.Black,
                ),
            ) { Text(if (lap.running) "PAUSE" else "START", fontWeight = FontWeight.Bold) }

            OutlinedButton(onClick = actions.onManualLap, modifier = Modifier.weight(1f)) { Text("LAP") }
            OutlinedButton(onClick = actions.onResetTimer) { Text("RESET") }
        }

        Text(
            text = "Laps split automatically at the chequered line.",
            color = TextMuted,
            fontSize = 9.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// --------------------------------------------------------------------- telemetry

@Composable
private fun TelemetryPanel(state: RaceUiState) {
    val t = state.telemetry
    val gForce = if (t.accelX != null && t.accelY != null && t.accelZ != null) {
        val magnitude = sqrt(
            (t.accelX.toDouble() * t.accelX) + (t.accelY.toDouble() * t.accelY) + (t.accelZ.toDouble() * t.accelZ)
        )
        "%.2f".format(magnitude / 1000.0)
    } else {
        "—"
    }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PanelLabel("Track sensors")
                Spacer(Modifier.height(6.dp))
                LineSensorView(t.lineCode, state.trackMode)
            }
            AssistBadge(state)
        }

        Spacer(Modifier.height(10.dp))
        ProximityGauge(t.distanceCm, Modifier.fillMaxWidth())

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TelemetryTile("G-force", gForce, "g", TrackCyan, Modifier.weight(1f))
            TelemetryTile("Temp", t.temperatureC?.toString() ?: "—", "°C", Color.White, Modifier.weight(1f))
            TelemetryTile("Light", t.lightLevel?.toString() ?: "—", null, Color.White, Modifier.weight(1f))
            TelemetryTile("Packets", t.packetsReceived.toString(), null, TextMuted, Modifier.weight(1f))
        }

        Spacer(Modifier.height(10.dp))
        PanelLabel("Motor output")
        Spacer(Modifier.height(6.dp))
        MotorBar("L", state.motors.left, Modifier.fillMaxWidth())
        Spacer(Modifier.height(4.dp))
        MotorBar("R", state.motors.right, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AssistBadge(state: RaceUiState) {
    if (!state.assistEnabled) {
        Text("MANUAL", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        return
    }
    val label = when (state.assistPhase) {
        AssistPhase.ON_TRACK -> "ASSIST · ON TRACK"
        AssistPhase.CORRECTING -> "ASSIST · CORRECTING"
        AssistPhase.MARKER -> "ASSIST · START/FINISH"
        AssistPhase.LOST -> "ASSIST · SEARCHING"
    }
    val accent = when (state.assistPhase) {
        AssistPhase.ON_TRACK -> SignalGreen
        AssistPhase.CORRECTING -> SignalAmber
        AssistPhase.MARKER -> Color.White
        AssistPhase.LOST -> TrackMagenta
    }
    val transition = rememberInfiniteTransition(label = "assist")
    val pulse by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    Text(text = label, color = accent.copy(alpha = pulse), fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

// ----------------------------------------------------------------------- controls

@Composable
private fun ControlPanel(state: RaceUiState, actions: RaceActions) {
    val driveEnabled = state.link.isReady

    Panel(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.controlMode == ControlMode.JOYSTICK) {
                JoystickPad(onInput = actions.onStick, enabled = driveEnabled, diameter = 184.dp)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ThrottleTrigger(onThrottle = actions.onTiltThrottle, enabled = driveEnabled, height = 184.dp)
                    Spacer(Modifier.height(6.dp))
                    Text("TILT TO STEER", fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StopButton(onClick = actions.onStop, enabled = driveEnabled)

                Button(
                    onClick = actions.onToggleAssist,
                    enabled = driveEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.assistEnabled) TrackCyan else CarbonLine,
                        contentColor = if (state.assistEnabled) Color.Black else Color.White,
                    ),
                ) {
                    Text(
                        text = if (state.assistEnabled) "ASSIST ON" else "ASSIST OFF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                OutlinedButton(
                    onClick = actions.onHorn,
                    enabled = driveEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("HORN") }
            }
        }

        if (state.controlMode == ControlMode.TILT) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = actions.onTareTilt, modifier = Modifier.fillMaxWidth()) {
                Text("Re-centre tilt to current grip")
            }
        }
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        SignalRed.copy(alpha = if (enabled) 1f else 0.3f),
                        SignalRed.copy(alpha = if (enabled) 0.72f else 0.2f),
                    )
                )
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("STOP", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
    }
}
