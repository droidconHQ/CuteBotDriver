package com.example.cutebotandroidsample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.cutebotandroidsample.race.ControlMode
import com.example.cutebotandroidsample.race.DiscoveredRobot
import com.example.cutebotandroidsample.race.LinkState
import com.example.cutebotandroidsample.race.RaceUiState
import com.example.cutebotandroidsample.race.TrackMode

/** Callbacks the screens need, bundled so each composable stays previewable. */
data class RaceActions(
    val onConnect: () -> Unit = {},
    val onConnectTo: (DiscoveredRobot) -> Unit = {},
    val onToggleScan: () -> Unit = {},
    val onStartScan: () -> Unit = {},
    val onStopScan: () -> Unit = {},
    val onDisconnect: () -> Unit = {},
    val onAddressChange: (String) -> Unit = {},
    val onTeamNameChange: (String) -> Unit = {},
    val onShowTeamName: () -> Unit = {},
    val onStick: (Float, Float) -> Unit = { _, _ -> },
    val onTiltThrottle: (Float) -> Unit = {},
    val onStop: () -> Unit = {},
    val onHorn: () -> Unit = {},
    val onToggleAssist: () -> Unit = {},
    val onSpeedCap: (Int) -> Unit = {},
    val onTrim: (Int) -> Unit = {},
    val onControlMode: (ControlMode) -> Unit = {},
    val onTareTilt: () -> Unit = {},
    val onTrackMode: (TrackMode) -> Unit = {},
    val onConeGuard: (Boolean) -> Unit = {},
    val onToggleTimer: () -> Unit = {},
    val onManualLap: () -> Unit = {},
    val onResetTimer: () -> Unit = {},
    val onLightsOff: () -> Unit = {},
    val onDismissBrownout: () -> Unit = {},
)

private enum class Destination { CONNECT, DRIVE, SETTINGS }

/**
 * Chooses the screen from the link's own state rather than from a tab the user has to
 * manage: no robot means the connect screen, a live robot means the drive screen.
 *
 * The one deliberate exception is a dropout mid-run. Being thrown out of the lap timer
 * because the radio hiccupped would be worse than useless at speed, so the driver stays
 * on the drive screen (with a banner) for as long as the link is still trying, and only
 * returns to the connect screen once it has actually given up.
 */
@Composable
fun RaceScreen(
    state: RaceUiState,
    actions: RaceActions,
    snackbarHostState: SnackbarHostState,
) {
    var showSettings by remember { mutableStateOf(false) }
    var hasConnected by remember { mutableStateOf(false) }

    if (state.link.isReady && !hasConnected) hasConnected = true
    val abandoned = state.link.state == LinkState.IDLE || state.link.state == LinkState.FAILED
    if (abandoned && hasConnected) hasConnected = false

    val destination = when {
        !hasConnected -> Destination.CONNECT
        showSettings -> Destination.SETTINGS
        else -> Destination.DRIVE
    }

    // Leaving the drive screen must never strand the car under power.
    LaunchedEffect(destination) {
        if (destination != Destination.DRIVE) actions.onStop()
    }

    BackHandler(enabled = destination == Destination.SETTINGS) { showSettings = false }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "destination",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { target ->
            Box(Modifier.fillMaxSize()) {
                when (target) {
                    Destination.CONNECT -> ConnectScreen(state, actions)
                    Destination.DRIVE -> DriveScreen(state, actions, onOpenSettings = { showSettings = true })
                    Destination.SETTINGS -> SettingsScreen(state, actions, onBack = { showSettings = false })
                }
            }
        }
    }
}
