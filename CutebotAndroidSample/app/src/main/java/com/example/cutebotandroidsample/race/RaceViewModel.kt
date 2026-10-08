package com.example.cutebotandroidsample.race

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How the driver supplies steering. */
enum class ControlMode { JOYSTICK, TILT }

/** One-shot occurrences the UI answers with haptics, a snackbar or a flash. */
sealed interface RaceEvent {
    data class LapRecorded(val lapMs: Long, val isBest: Boolean) : RaceEvent
    data object ObstacleStop : RaceEvent
    data object AssistDisengaged : RaceEvent
    data class Notice(val message: String) : RaceEvent
}

data class RaceUiState(
    val link: LinkStatus = LinkStatus(),
    val telemetry: Telemetry = Telemetry(),
    val lap: LapTimerState = LapTimerState(),
    val address: String = "",
    val teamName: String = "",
    val speedCap: Int = 70,
    val trim: Int = 0,
    val controlMode: ControlMode = ControlMode.JOYSTICK,
    val tiltAvailable: Boolean = false,
    val assistEnabled: Boolean = false,
    val assistPhase: AssistPhase = AssistPhase.ON_TRACK,
    val trackMode: TrackMode = TrackMode.EDGE_AVOID,
    val coneGuardEnabled: Boolean = true,
    val proximityAlert: Boolean = false,
    val motors: MotorPair = MotorPair.STOPPED,
    val txPerSecond: Int = 0,
    val scanning: Boolean = false,
    val discovered: List<DiscoveredRobot> = emptyList(),
    val scanError: String? = null,
)

/**
 * Race-day brain: owns the link, the control mixers and the stopwatch, and publishes one
 * immutable [RaceUiState] for Compose to render.
 */
class RaceViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = RacePrefs(app)
    private val link = RobotLink(app, viewModelScope)
    private val assist = TrackAssist()
    private val markers = MarkerDetector()
    private val lapTimer = LapTimer()
    private val tilt = TiltSensor(app)
    private val scanner = RobotScanner(app)
    private val signals = SignalDirector { link.queue.enqueue(it) }

    private val _state = MutableStateFlow(
        RaceUiState(
            address = prefs.address,
            teamName = prefs.teamName,
            speedCap = prefs.speedCap,
            trim = prefs.trim,
            controlMode = if (prefs.tiltMode) ControlMode.TILT else ControlMode.JOYSTICK,
            tiltAvailable = tilt.available,
        )
    )
    val state: StateFlow<RaceUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<RaceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<RaceEvent> = _events.asSharedFlow()

    /** Throttle demand in tilt mode, supplied by the on-screen trigger. */
    private var tiltThrottle = 0f
    private var lastTxCount = 0L

    init {
        link.onLineCode = ::onLineCode
        viewModelScope.launch {
            link.status.collect { s ->
                _state.update { it.copy(link = s) }
                // Scanning while connected wastes radio time and adds latency.
                if (s.isReady) stopScan()
            }
        }
        viewModelScope.launch { scanner.results.collect { r -> _state.update { it.copy(discovered = r) } } }
        viewModelScope.launch { scanner.scanning.collect { b -> _state.update { it.copy(scanning = b) } } }
        viewModelScope.launch { scanner.error.collect { e -> _state.update { it.copy(scanError = e) } } }
        viewModelScope.launch { link.telemetry.collect(::onTelemetry) }
        viewModelScope.launch { blinkLoop() }
        viewModelScope.launch { uiTickLoop() }
        if (_state.value.controlMode == ControlMode.TILT) tilt.start()
    }

    // ------------------------------------------------------------------- connection

    fun connect() {
        stopScan()
        prefs.address = _state.value.address
        link.connect(_state.value.address)
    }

    /** Connects to a robot picked off the scan list — the path that carries its address type. */
    fun connectTo(robot: DiscoveredRobot) {
        stopScan()
        prefs.address = robot.address
        _state.update { it.copy(address = robot.address) }
        link.connect(robot.device)
    }

    fun startScan() = scanner.start()

    fun stopScan() = scanner.stop()

    fun toggleScan() = if (_state.value.scanning) stopScan() else startScan()

    fun disconnect() {
        assistOff()
        link.disconnect()
    }

    fun setAddress(value: String) = _state.update { it.copy(address = value) }

    fun setTeamName(value: String) {
        if (value.isNotBlank()) prefs.teamName = value
        _state.update { it.copy(teamName = value) }
    }

    /** Announces the team on the 5x5 matrix — our signature at the start line. */
    fun showTeamName() {
        link.queue.enqueue("DISP,${_state.value.teamName.ifBlank { "NEXT APP" }}")
    }

    fun dismissBrownoutWarning() = link.clearBrownoutWarning()

    // ------------------------------------------------------------------------ setup

    fun setSpeedCap(value: Int) {
        val capped = value.coerceIn(DriveMixer.DEADBAND_FLOOR, DriveMixer.MAX_SPEED)
        prefs.speedCap = capped
        _state.update { it.copy(speedCap = capped) }
    }

    fun setTrim(value: Int) {
        val capped = value.coerceIn(-30, 30)
        prefs.trim = capped
        _state.update { it.copy(trim = capped) }
    }

    fun setControlMode(mode: ControlMode) {
        prefs.tiltMode = mode == ControlMode.TILT
        if (mode == ControlMode.TILT) tilt.start() else tilt.stop()
        tiltThrottle = 0f
        stop()
        _state.update { it.copy(controlMode = mode) }
    }

    fun tareTilt() {
        tilt.tare()
        _events.tryEmit(RaceEvent.Notice("Tilt centre captured"))
    }

    fun setTrackMode(mode: TrackMode) {
        assist.mode = mode
        _state.update { it.copy(trackMode = mode) }
    }

    fun setConeGuard(enabled: Boolean) = _state.update { it.copy(coneGuardEnabled = enabled) }

    // ------------------------------------------------------------------- driving

    /** Joystick input. Any real deflection hands control back to the driver. */
    fun onStick(throttle: Float, steer: Float) {
        if (_state.value.assistEnabled && (abs(throttle) > HANDOVER_DEADZONE || abs(steer) > HANDOVER_DEADZONE)) {
            assistOff()
            _events.tryEmit(RaceEvent.AssistDisengaged)
        }
        if (_state.value.assistEnabled) return
        val s = _state.value
        applyMotors(DriveMixer.mix(throttle, steer, s.speedCap, s.trim))
    }

    /** Throttle trigger in tilt mode; steering comes from the accelerometer loop. */
    fun onTiltThrottle(throttle: Float) {
        if (_state.value.assistEnabled && abs(throttle) > HANDOVER_DEADZONE) {
            assistOff()
            _events.tryEmit(RaceEvent.AssistDisengaged)
        }
        tiltThrottle = throttle
    }

    /** Immediate halt — bypasses the paced queue. */
    fun stop() {
        tiltThrottle = 0f
        if (_state.value.assistEnabled) assistOff()
        link.queue.sendNow("S")
        _state.update { it.copy(motors = MotorPair.STOPPED) }
        signals.update(signalInputs(MotorPair.STOPPED))
    }

    fun toggleAssist() {
        if (_state.value.assistEnabled) assistOff() else assistOn()
    }

    private fun assistOn() {
        assist.reset()
        link.queue.telemetryPlan = CommandQueue.LINE_FOCUSED_PLAN
        _state.update { it.copy(assistEnabled = true, assistPhase = AssistPhase.ON_TRACK) }
        link.queue.enqueue("ICON,YES")
    }

    private fun assistOff() {
        if (!_state.value.assistEnabled) return
        link.queue.telemetryPlan = CommandQueue.DEFAULT_TELEMETRY_PLAN
        _state.update { it.copy(assistEnabled = false) }
        link.queue.sendNow("S")
        link.queue.enqueue("ICON,HAPPY")
    }

    // --------------------------------------------------------------------- effects

    fun horn() = link.queue.enqueue("HORN")

    fun showIcon(name: String) = link.queue.enqueue("ICON,$name")

    fun lightsOff() {
        signals.allOff()
    }

    // ----------------------------------------------------------------- lap timing

    fun toggleTimer() {
        val running = lapTimer.snapshot().running
        if (running) {
            lapTimer.stop()
        } else {
            lapTimer.start()
            if (lapTimer.snapshot().laps.isEmpty()) link.queue.enqueue("DISP,0")
        }
        markers.reset()
        pushLapState()
    }

    fun manualLap() = recordLap(automatic = false)

    fun resetTimer() {
        lapTimer.reset()
        markers.reset()
        link.queue.enqueue("CLS")
        pushLapState()
    }

    /**
     * Records a lap and mirrors the new lap count onto the robot's 5x5 matrix, so a
     * spectator at the barrier can read the car's progress without looking at the phone.
     * A session best is announced with a heart before the number.
     */
    private fun recordLap(automatic: Boolean) {
        lapTimer.lap()
        val snap = lapTimer.snapshot()
        val last = snap.lastLapMs
        if (last != null) {
            val isBest = last == snap.bestLapMs
            _events.tryEmit(RaceEvent.LapRecorded(last, isBest))
            if (automatic) link.queue.enqueue("BEEP")
            if (isBest && snap.laps.size > 1) link.queue.enqueue("ICON,HEART")
            link.queue.enqueue("DISP,${snap.laps.size}")
        }
        pushLapState()
    }

    // ------------------------------------------------------------------ internals

    /**
     * Telemetry-driven line handling. Marker detection runs regardless of assist so laps
     * are timed automatically even on a fully manual run.
     */
    private fun onLineCode(code: Int) {
        if (markers.onCode(code) && lapTimer.snapshot().running) {
            recordLap(automatic = true)
        }

        if (!_state.value.assistEnabled) return
        val output = assist.onLine(code)
        _state.update { it.copy(assistPhase = output.phase) }
        applyMotors(output.motors)
    }

    private fun onTelemetry(telemetry: Telemetry) {
        val distance = telemetry.distanceCm
        val alert = _state.value.coneGuardEnabled && distance != null && distance in 1..CONE_WARN_CM
        _state.update { it.copy(telemetry = telemetry, proximityAlert = alert) }
    }

    /**
     * Final gate before the wire: applies the cone guard, publishes the motor state and
     * lets the signalling layer react.
     */
    private fun applyMotors(requested: MotorPair) {
        val guarded = applyConeGuard(requested)
        _state.update { it.copy(motors = guarded) }
        if (guarded == MotorPair.STOPPED) {
            link.queue.sendNow("S")
        } else {
            link.queue.setDrive(guarded.toCommand())
        }
        signals.update(signalInputs(guarded))
    }

    /**
     * The mat is littered with cones. When the ultrasonic sees one closing while we are
     * driving forward, forward speed is halved and then cut entirely — reverse and pivots
     * stay available so the driver can back out.
     */
    private fun applyConeGuard(motors: MotorPair): MotorPair {
        val s = _state.value
        if (!s.coneGuardEnabled) return motors
        val distance = s.telemetry.distanceCm ?: return motors
        val drivingForward = motors.left > 0 && motors.right > 0
        if (!drivingForward || distance <= 0) return motors

        return when {
            distance <= CONE_STOP_CM -> {
                _events.tryEmit(RaceEvent.ObstacleStop)
                MotorPair.STOPPED
            }
            distance <= CONE_WARN_CM -> MotorPair(
                (motors.left / 2).coerceAtLeast(DriveMixer.DEADBAND_FLOOR),
                (motors.right / 2).coerceAtLeast(DriveMixer.DEADBAND_FLOOR),
            )
            else -> motors
        }
    }

    private fun signalInputs(motors: MotorPair): SignalInputs {
        val s = _state.value
        return SignalInputs(
            motors = motors,
            connected = s.link.isReady,
            linkStale = s.link.stale,
            following = s.assistEnabled,
            assistPhase = s.assistPhase,
        )
    }

    private fun pushLapState() = _state.update { it.copy(lap = lapTimer.snapshot()) }

    /** Drives the turn-signal blink phase. */
    private suspend fun blinkLoop() {
        while (true) {
            delay(BLINK_MS)
            if (_state.value.link.isReady) signals.blinkTick()
        }
    }

    /** Refreshes the stopwatch readout, tilt steering and the TX rate counter. */
    private suspend fun uiTickLoop() {
        var lastRateSample = System.currentTimeMillis()
        while (true) {
            delay(UI_TICK_MS)
            val s = _state.value

            if (s.lap.running) pushLapState()

            if (s.controlMode == ControlMode.TILT && !s.assistEnabled) {
                applyMotors(DriveMixer.mix(tiltThrottle, tilt.steer, s.speedCap, s.trim))
            }

            val now = System.currentTimeMillis()
            if (now - lastRateSample >= 1_000L) {
                val sent = link.queue.sentCount
                val rate = (sent - lastTxCount).toInt()
                lastTxCount = sent
                lastRateSample = now
                _state.update { it.copy(txPerSecond = rate) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        tilt.stop()
        scanner.stop()
        link.disconnect()
    }

    private companion object {
        const val BLINK_MS = 320L
        const val UI_TICK_MS = 60L
        const val HANDOVER_DEADZONE = 0.12f
        const val CONE_WARN_CM = 18
        const val CONE_STOP_CM = 9
    }
}
