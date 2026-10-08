package com.example.cutebotandroidsample

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cutebotandroidsample.race.RaceEvent
import com.example.cutebotandroidsample.race.RaceViewModel
import com.example.cutebotandroidsample.race.formatLapTime
import com.example.cutebotandroidsample.ui.RaceActions
import com.example.cutebotandroidsample.ui.RaceScreen
import com.example.cutebotandroidsample.ui.theme.CutebotAndroidSampleTheme

/**
 * Single-activity host for the race controller.
 *
 * The activity itself stays deliberately thin: BLE state, telemetry and control mixing all
 * live in [RaceViewModel] so they survive a rotation or a brief trip to the background
 * mid-run rather than tearing down the GATT connection.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A screen that sleeps mid-lap is a car that keeps its last command.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        requestBlePermissions()

        setContent {
            CutebotAndroidSampleTheme {
                val viewModel: RaceViewModel = viewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val haptics = LocalHapticFeedback.current

                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is RaceEvent.LapRecorded -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val suffix = if (event.isBest) " · session best" else ""
                                snackbarHostState.showSnackbar(
                                    message = "Lap ${formatLapTime(event.lapMs)}$suffix",
                                    duration = SnackbarDuration.Short,
                                )
                            }

                            RaceEvent.ObstacleStop -> {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }

                            RaceEvent.AssistDisengaged -> {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                snackbarHostState.showSnackbar(
                                    message = "Assist disengaged — manual control",
                                    duration = SnackbarDuration.Short,
                                )
                            }

                            is RaceEvent.Notice -> {
                                snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                            }
                        }
                    }
                }

                RaceScreen(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    actions = RaceActions(
                        onConnect = viewModel::connect,
                        onConnectTo = viewModel::connectTo,
                        onToggleScan = viewModel::toggleScan,
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onDisconnect = viewModel::disconnect,
                        onAddressChange = viewModel::setAddress,
                        onTeamNameChange = viewModel::setTeamName,
                        onShowTeamName = viewModel::showTeamName,
                        onStick = viewModel::onStick,
                        onTiltThrottle = viewModel::onTiltThrottle,
                        onStop = viewModel::stop,
                        onHorn = viewModel::horn,
                        onToggleAssist = viewModel::toggleAssist,
                        onSpeedCap = viewModel::setSpeedCap,
                        onTrim = viewModel::setTrim,
                        onControlMode = viewModel::setControlMode,
                        onTareTilt = viewModel::tareTilt,
                        onTrackMode = viewModel::setTrackMode,
                        onConeGuard = viewModel::setConeGuard,
                        onToggleTimer = viewModel::toggleTimer,
                        onManualLap = viewModel::manualLap,
                        onResetTimer = viewModel::resetTimer,
                        onLightsOff = viewModel::lightsOff,
                        onDismissBrownout = viewModel::dismissBrownoutWarning,
                    ),
                )
            }
        }
    }

    /**
     * Android 12+ gates BLE behind the runtime scan/connect permissions; earlier releases
     * route discovery through location instead, as CONTEST.md warns.
     */
    private fun requestBlePermissions() {
        val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        launcher.launch(permissions)
    }
}
