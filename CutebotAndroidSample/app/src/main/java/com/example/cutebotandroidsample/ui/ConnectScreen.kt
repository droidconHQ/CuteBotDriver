package com.example.cutebotandroidsample.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cutebotandroidsample.race.DiscoveredRobot
import com.example.cutebotandroidsample.race.LinkState
import com.example.cutebotandroidsample.race.RaceUiState
import com.example.cutebotandroidsample.ui.theme.CarbonLine
import com.example.cutebotandroidsample.ui.theme.CarbonRaised
import com.example.cutebotandroidsample.ui.theme.SignalAmber
import com.example.cutebotandroidsample.ui.theme.SignalGreen
import com.example.cutebotandroidsample.ui.theme.TextMuted
import com.example.cutebotandroidsample.ui.theme.TrackBlue
import com.example.cutebotandroidsample.ui.theme.TrackCyan
import com.example.cutebotandroidsample.ui.theme.TrackMagenta
import com.example.cutebotandroidsample.ui.theme.TrackPurple

/**
 * The app's front door, shown until a robot is on the other end of the link.
 *
 * It carries one job and one call to action: everything tunable lives behind the drive
 * screen's settings, because on race morning the only question this screen has to answer
 * is "am I talking to my car yet".
 *
 * Robots are picked off a live scan rather than typed as a MAC. That is not only kinder at
 * a noisy booth — a scanned handle carries the peripheral's address type, which a
 * hand-built one does not, and the micro:bit's random static address needs it.
 */
@Composable
fun ConnectScreen(
    state: RaceUiState,
    actions: RaceActions,
    modifier: Modifier = Modifier,
) {
    val link = state.link
    val busy = link.state == LinkState.CONNECTING ||
        link.state == LinkState.DISCOVERING ||
        link.state == LinkState.RECONNECTING
    var manualEntry by remember { mutableStateOf(false) }

    // Start looking as soon as the screen appears: the racer should not have to ask.
    DisposableEffect(manualEntry) {
        if (!manualEntry) actions.onStartScan()
        onDispose { actions.onStopScan() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(18.dp))
        PulseRing(active = busy || state.scanning)
        Spacer(Modifier.height(14.dp))

        Text(
            text = "ROBOT RALLY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = TextMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.teamName.ifBlank { "NEXT APP" },
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))
        ConnectionStatusLine(link)
        Spacer(Modifier.height(18.dp))

        if (manualEntry) {
            ManualAddressEntry(state, actions, busy)
        } else {
            RobotPicker(state, actions, busy)
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { manualEntry = !manualEntry }) {
            Text(
                text = if (manualEntry) "Find robots nearby instead" else "Enter a MAC address instead",
                color = TextMuted,
                fontSize = 12.sp,
            )
        }

        AnimatedVisibility(visible = link.brownoutSuspected) {
            Box(Modifier.padding(top = 14.dp)) {
                BrownoutBanner(onDismiss = actions.onDismissBrownout)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------- scan picker

@Composable
private fun RobotPicker(state: RaceUiState, actions: RaceActions, busy: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        state.discovered.forEach { robot ->
            RobotRow(
                robot = robot,
                enabled = !busy,
                onClick = { actions.onConnectTo(robot) },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.discovered.isEmpty()) {
            Text(
                text = when {
                    state.scanning -> "Scanning for robots…"
                    state.scanError != null -> state.scanError
                    else -> "Tap scan to find the robots around you. Check yours is switched on at the back of the chassis."
                },
                color = if (state.scanError != null) SignalAmber else TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp),
            )
        }

        Button(
            onClick = actions.onToggleScan,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.scanning) CarbonLine else TrackMagenta,
                contentColor = Color.White,
                disabledContainerColor = TrackMagenta.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = when {
                    busy -> "CONNECTING…"
                    state.scanning -> "STOP SCANNING"
                    else -> "SCAN FOR ROBOTS"
                },
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

/** One discovered robot: its chassis name, MAC and signal strength. */
@Composable
private fun RobotRow(robot: DiscoveredRobot, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarbonRaised)
            .border(1.dp, CarbonLine, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = robot.shortName?.uppercase() ?: robot.name ?: "UNKNOWN ROBOT",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
            Text(
                text = robot.address,
                color = TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
        }
        SignalPips(robot.rssi)
        Spacer(Modifier.width(10.dp))
        Text("CONNECT", color = TrackMagenta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Four pips of RSSI, so the robot in your hand is obvious among a booth full of them. */
@Composable
private fun SignalPips(rssi: Int) {
    val bars = when {
        rssi >= -55 -> 4
        rssi >= -70 -> 3
        rssi >= -85 -> 2
        else -> 1
    }
    Canvas(Modifier.width(22.dp).height(14.dp)) {
        val barWidth = size.width / 7f
        repeat(4) { index ->
            val fraction = 0.32f + index * 0.226f
            val barHeight = size.height * fraction
            drawRect(
                color = if (index < bars) SignalGreen else Color.White.copy(alpha = 0.14f),
                topLeft = Offset(index * barWidth * 1.75f, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
            )
        }
    }
}

// ------------------------------------------------------------------ manual entry

@Composable
private fun ManualAddressEntry(state: RaceUiState, actions: RaceActions, busy: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = state.address,
            onValueChange = actions.onAddressChange,
            label = { Text("Robot MAC address") },
            placeholder = { Text("AA:BB:CC:DD:EE:FF") },
            singleLine = true,
            enabled = !busy,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 17.sp,
                color = Color.White,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Printed on the chassis label, under the robot's name.",
            fontSize = 10.sp,
            color = TextMuted,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = actions.onConnect,
            enabled = !busy && state.address.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TrackMagenta,
                contentColor = Color.White,
                disabledContainerColor = TrackMagenta.copy(alpha = 0.25f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Text(
                text = if (busy) "CONNECTING…" else "CONNECT",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                letterSpacing = 1.sp,
            )
        }
    }
}

// ------------------------------------------------------------------------ chrome

/**
 * Concentric rings in the track's own gradient, breathing whenever the radio is busy so
 * the wait has a heartbeat rather than a frozen screen.
 */
@Composable
private fun PulseRing(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "connect")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Restart),
        label = "phase",
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp),
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val base = size.height / 2.6f

        if (active) {
            val radius = base * (1f + phase * 1.1f)
            drawCircle(
                color = TrackMagenta.copy(alpha = (1f - phase) * 0.35f),
                radius = radius,
                center = centre,
                style = Stroke(width = 3f),
            )
        }

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(TrackBlue, TrackPurple, TrackMagenta),
                start = Offset(centre.x - base, centre.y - base),
                end = Offset(centre.x + base, centre.y + base),
            ),
            radius = base,
            center = centre,
            style = Stroke(width = 6f),
        )
        drawCircle(
            color = TrackCyan.copy(alpha = 0.5f),
            radius = base * 0.62f,
            center = centre,
            style = Stroke(width = 2f),
        )
        drawCircle(
            brush = Brush.linearGradient(listOf(TrackMagenta, TrackPurple)),
            radius = base * 0.22f,
            center = centre,
        )
    }
}
