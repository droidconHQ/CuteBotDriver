package com.example.cutebotandroidsample.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cutebotandroidsample.race.LinkState
import com.example.cutebotandroidsample.race.LinkStatus
import com.example.cutebotandroidsample.ui.theme.CarbonLine
import com.example.cutebotandroidsample.ui.theme.CarbonRaised
import com.example.cutebotandroidsample.ui.theme.SignalAmber
import com.example.cutebotandroidsample.ui.theme.SignalGreen
import com.example.cutebotandroidsample.ui.theme.SignalRed
import com.example.cutebotandroidsample.ui.theme.TextMuted
import com.example.cutebotandroidsample.ui.theme.TrackCyan
import com.example.cutebotandroidsample.ui.theme.TrackMagenta
import com.example.cutebotandroidsample.ui.theme.TrackPurple

/** Colour that a given link state should be reported in, consistently across screens. */
fun accentFor(link: LinkStatus): Color = when {
    link.stale -> SignalAmber
    link.state == LinkState.READY -> SignalGreen
    link.state == LinkState.FAILED -> SignalRed
    link.state == LinkState.IDLE -> TextMuted
    else -> TrackCyan
}

/** Small coloured dot plus the link's own description of itself. */
@Composable
fun ConnectionStatusLine(link: LinkStatus, modifier: Modifier = Modifier) {
    val accent = accentFor(link)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (link.stale) "${link.detail} · no telemetry" else link.detail,
            fontSize = 12.sp,
            color = accent,
        )
    }
}

/**
 * Battery warning raised when a disconnect/reconnect pair lands inside the brownout
 * window. CONTEST.md is explicit that this is the AAA pack sagging under motor inrush,
 * so the copy points at the batteries rather than at the Bluetooth stack.
 */
@Composable
fun BrownoutBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SignalAmber.copy(alpha = 0.14f))
            .border(1.dp, SignalAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Battery brownout suspected", color = SignalAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "The robot dropped and re-joined within 3s — that is the AAA pack sagging under motor load, not your app. Swap in fresh cells.",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onDismiss) { Text("OK", fontSize = 12.sp) }
    }
}

/** Caption above a monospaced figure, used for lap stats. */
@Composable
fun StatLabel(label: String, value: String, accent: Color) {
    Column {
        PanelLabel(label)
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = accent,
        )
    }
}

/** Labelled slider with a live value readout on the right. */
@Composable
fun TunerRow(
    label: String,
    value: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(value, color = TrackMagenta, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        }
        Slider(
            value = sliderValue,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = TrackMagenta,
                activeTrackColor = TrackPurple,
                inactiveTrackColor = CarbonLine,
            ),
        )
    }
}

/** Two-or-more way exclusive choice, styled to match the race instrument panels. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarbonRaised)
            .border(1.dp, CarbonLine, RoundedCornerShape(10.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            val background by animateColorAsState(
                if (selected) TrackMagenta else Color.Transparent,
                tween(150),
                label = "segment",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(background)
                    .clickable(enabled = enabled && !selected) { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = if (selected) Color.White else TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
