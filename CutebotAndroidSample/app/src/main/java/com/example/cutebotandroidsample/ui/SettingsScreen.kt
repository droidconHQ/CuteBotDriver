package com.example.cutebotandroidsample.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cutebotandroidsample.race.ControlMode
import com.example.cutebotandroidsample.race.DriveMixer
import com.example.cutebotandroidsample.race.RaceUiState
import com.example.cutebotandroidsample.race.TrackMode
import com.example.cutebotandroidsample.ui.theme.SignalAmber
import com.example.cutebotandroidsample.ui.theme.SignalRed
import com.example.cutebotandroidsample.ui.theme.TextMuted
import com.example.cutebotandroidsample.ui.theme.TrackMagenta
import kotlin.math.roundToInt

/**
 * Everything that is tuned between runs rather than during one: team identity, the two
 * numbers that decide how the car drives, how it is steered, how the floor is interpreted
 * and the obstacle guard. Disconnecting lives here too, well away from a racing thumb.
 */
@Composable
fun SettingsScreen(
    state: RaceUiState,
    actions: RaceActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to driving",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.size(12.dp))
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(Modifier.height(14.dp))

        // ---------------------------------------------------------------- robot
        Panel(Modifier.fillMaxWidth()) {
            PanelLabel("Connected robot")
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.address.ifBlank { "—" },
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                color = Color.White,
            )
            ConnectionStatusLine(state.link, Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = actions.onDisconnect,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Disconnect", color = SignalRed) }
        }

        Spacer(Modifier.height(10.dp))

        // ----------------------------------------------------------------- team
        Panel(Modifier.fillMaxWidth()) {
            PanelLabel("Team")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.teamName,
                onValueChange = actions.onTeamNameChange,
                label = { Text("Name shown on the 5x5 matrix") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onShowTeamName, modifier = Modifier.weight(1f)) {
                    Text("Scroll on robot")
                }
                OutlinedButton(onClick = actions.onLightsOff, modifier = Modifier.weight(1f)) {
                    Text("Lights off")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --------------------------------------------------------------- tuning
        Panel(Modifier.fillMaxWidth()) {
            PanelLabel("Tuning")
            Spacer(Modifier.height(10.dp))

            TunerRow(
                label = "Speed cap",
                value = "${state.speedCap}%",
                sliderValue = state.speedCap.toFloat(),
                range = DriveMixer.DEADBAND_FLOOR.toFloat()..DriveMixer.MAX_SPEED.toFloat(),
                onChange = { actions.onSpeedCap(it.roundToInt()) },
            )

            TunerRow(
                label = "Motor trim",
                value = when {
                    state.trim == 0 -> "centre"
                    state.trim > 0 -> "+${state.trim} left"
                    else -> "${-state.trim} right"
                },
                sliderValue = state.trim.toFloat(),
                range = -30f..30f,
                onChange = { actions.onTrim(it.roundToInt()) },
            )
            Text(
                text = "Trim corrects an un-encoded motor that pulls to one side. Set it on a straight, then leave it — it is saved between runs.",
                color = TextMuted,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.height(10.dp))

        // -------------------------------------------------------------- control
        Panel(Modifier.fillMaxWidth()) {
            PanelLabel("Steering")
            Spacer(Modifier.height(8.dp))
            SegmentedToggle(
                options = listOf("JOYSTICK", "TILT"),
                selectedIndex = state.controlMode.ordinal,
                onSelect = { actions.onControlMode(ControlMode.entries[it]) },
                enabled = state.tiltAvailable || state.controlMode == ControlMode.TILT,
            )
            if (!state.tiltAvailable) {
                Text("No accelerometer detected on this phone.", color = SignalAmber, fontSize = 10.sp)
            }

            Spacer(Modifier.height(14.dp))
            PanelLabel("Track interpretation")
            Spacer(Modifier.height(8.dp))
            SegmentedToggle(
                options = listOf("EDGE AVOID", "LINE FOLLOW"),
                selectedIndex = state.trackMode.ordinal,
                onSelect = { actions.onTrackMode(TrackMode.entries[it]) },
            )
            Text(
                text = when (state.trackMode) {
                    TrackMode.EDGE_AVOID ->
                        "Rally mat: dark racing surface with white boundary lines. The assist steers away from white."
                    TrackMode.LINE_FOLLOW ->
                        "Classic mat: a dark line on a light floor. The assist tracks the line itself."
                },
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cone guard", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Halves forward speed inside 18cm and stops at 9cm, using the ultrasonic sensor.",
                        color = TextMuted,
                        fontSize = 10.sp,
                    )
                }
                Switch(
                    checked = state.coneGuardEnabled,
                    onCheckedChange = actions.onConeGuard,
                    colors = SwitchDefaults.colors(checkedTrackColor = TrackMagenta),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
