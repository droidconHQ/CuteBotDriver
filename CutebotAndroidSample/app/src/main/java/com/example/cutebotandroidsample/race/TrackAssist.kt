package com.example.cutebotandroidsample.race

/**
 * Decoded state of the Cutebot's two downward infrared sensors.
 *
 * Firmware codes (`?LINE`) are reported in terms of "black": 0 = neither sensor sees
 * black, 1 = right sensor on black, 2 = left sensor on black, 3 = both. The names here
 * restate that as what each sensor is actually over, because on the Robot Rally mat the
 * dark reading is the *racing surface* rather than a line.
 */
enum class SensorPair(val code: Int) {
    /** Both sensors over white — the car is square-on across a boundary or the chequer. */
    BOTH_WHITE(0),

    /** Right sensor dark, left sensor white: the left wheel has found a boundary. */
    RIGHT_DARK(1),

    /** Left sensor dark, right sensor white: the right wheel has found a boundary. */
    LEFT_DARK(2),

    /** Both sensors over dark vinyl — fully on the racing surface. */
    BOTH_DARK(3);

    companion object {
        fun from(code: Int): SensorPair = entries.firstOrNull { it.code == code } ?: BOTH_WHITE
    }
}

/**
 * How the white lines on the floor should be interpreted.
 *
 * The Robot Rally mat is a dark gradient bounded by white lines, so the car must be kept
 * *between* them ([EDGE_AVOID]). The classic Cutebot arrangement — a dark line on a light
 * floor, to be tracked rather than avoided — is retained as [LINE_FOLLOW] so the same
 * build works on a practice mat or any other track.
 */
enum class TrackMode { EDGE_AVOID, LINE_FOLLOW }

/** What the assist is doing right now, surfaced on the dashboard. */
enum class AssistPhase {
    /** Clean surface under both sensors, running at full assist speed. */
    ON_TRACK,

    /** A boundary is under one sensor; steering away from it. */
    CORRECTING,

    /** Square-on across a white line — almost always the chequered start/finish. */
    MARKER,

    /** Reference surface lost for long enough to start sweeping for it. */
    LOST,
}

data class AssistOutput(
    val motors: MotorPair,
    val phase: AssistPhase,
    /** True on the leading edge of a start/finish crossing, for automatic lap splits. */
    val markerCrossed: Boolean = false,
)

/**
 * Closed-loop steering assist fed by `?LINE` telemetry.
 *
 * In [TrackMode.EDGE_AVOID] the controller runs flat out while both sensors see dark
 * vinyl and applies a differential correction the instant either one catches a white
 * boundary, pushing the car back toward the middle of the lane. Correction strength
 * escalates on consecutive hits from the same side, so a gentle drift gets a nudge while a
 * car arriving at a hairpin gets a genuine pivot.
 *
 * [SensorPair.BOTH_WHITE] is special. Running square across a white line happens in
 * exactly one place on a closed circuit — the start/finish chequer — so the first frame of
 * a crossing raises [AssistOutput.markerCrossed] for the lap timer, debounced by
 * [markerCooldownFrames] to absorb the chequer's alternating squares.
 */
class TrackAssist(
    var mode: TrackMode = TrackMode.EDGE_AVOID,
    private val baseSpeed: Int = 55,
    private val correction: Int = 24,
    private val maxCorrection: Int = 48,
    private val searchSpeed: Int = 30,
    private val lostGraceFrames: Int = 3,
    private val markerCooldownFrames: Int = 12,
) {
    /** -1 = correction last applied away from the left, +1 = away from the right. */
    private var lastCorrectionSide = 0
    private var sameSideStreak = 0
    private var offSurfaceStreak = 0
    private var markerCooldown = 0
    private var inMarker = false

    var phase: AssistPhase = AssistPhase.ON_TRACK
        private set

    fun reset() {
        lastCorrectionSide = 0
        sameSideStreak = 0
        offSurfaceStreak = 0
        markerCooldown = 0
        inMarker = false
        phase = AssistPhase.ON_TRACK
    }

    fun onLine(code: Int): AssistOutput = onLine(SensorPair.from(code))

    fun onLine(reading: SensorPair): AssistOutput {
        if (markerCooldown > 0) markerCooldown--
        return when (mode) {
            TrackMode.EDGE_AVOID -> edgeAvoid(reading)
            TrackMode.LINE_FOLLOW -> lineFollow(reading)
        }
    }

    // ---------------------------------------------------------------- edge avoidance

    private fun edgeAvoid(reading: SensorPair): AssistOutput = when (reading) {
        SensorPair.BOTH_DARK -> {
            // Clean surface: reset the escalation and commit to the straight.
            resetCorrection()
            offSurfaceStreak = 0
            inMarker = false
            phase = AssistPhase.ON_TRACK
            AssistOutput(straight(baseSpeed), phase)
        }

        // Left sensor found white -> boundary is to the left -> steer right.
        SensorPair.RIGHT_DARK -> {
            offSurfaceStreak = 0
            inMarker = false
            AssistOutput(steerAway(side = 1), phase)
        }

        // Right sensor found white -> boundary is to the right -> steer left.
        SensorPair.LEFT_DARK -> {
            offSurfaceStreak = 0
            inMarker = false
            AssistOutput(steerAway(side = -1), phase)
        }

        SensorPair.BOTH_WHITE -> {
            val crossing = !inMarker && markerCooldown == 0
            if (crossing) {
                inMarker = true
                markerCooldown = markerCooldownFrames
            }
            offSurfaceStreak++
            if (offSurfaceStreak <= lostGraceFrames) {
                // Drive straight through the chequer rather than fighting it.
                phase = AssistPhase.MARKER
                AssistOutput(straight(baseSpeed), phase, markerCrossed = crossing)
            } else {
                phase = AssistPhase.LOST
                AssistOutput(sweep(), phase, markerCrossed = crossing)
            }
        }
    }

    /**
     * Applies an escalating differential away from [side] (+1 = steer right, -1 = left).
     * Holding a boundary for several frames means the correction was too gentle, so it
     * grows toward [maxCorrection] instead of scrubbing along the line.
     */
    private fun steerAway(side: Int): MotorPair {
        if (side == lastCorrectionSide) sameSideStreak++ else sameSideStreak = 0
        lastCorrectionSide = side
        phase = AssistPhase.CORRECTING

        val strength = (correction + sameSideStreak * CORRECTION_STEP).coerceAtMost(maxCorrection)
        // Slow the inside wheel as the correction grows; a hairpin becomes a pivot.
        val inner = baseSpeed - strength
        val outer = baseSpeed
        return if (side > 0) MotorPair(clamp(outer), clamp(inner)) else MotorPair(clamp(inner), clamp(outer))
    }

    // ------------------------------------------------------------- classic following

    private fun lineFollow(reading: SensorPair): AssistOutput = when (reading) {
        // Right sensor on the line -> the chassis sits left of it -> steer right.
        SensorPair.RIGHT_DARK -> {
            offSurfaceStreak = 0
            AssistOutput(steerAway(side = 1), phase)
        }

        SensorPair.LEFT_DARK -> {
            offSurfaceStreak = 0
            AssistOutput(steerAway(side = -1), phase)
        }

        SensorPair.BOTH_DARK -> {
            resetCorrection()
            offSurfaceStreak = 0
            phase = AssistPhase.ON_TRACK
            AssistOutput(straight(baseSpeed), phase)
        }

        // On a line narrower than the sensor gap this means "perfectly centred", so it is
        // only treated as a lost line once it persists past the grace window.
        SensorPair.BOTH_WHITE -> {
            offSurfaceStreak++
            if (offSurfaceStreak <= lostGraceFrames) {
                phase = AssistPhase.ON_TRACK
                AssistOutput(straight(baseSpeed), phase)
            } else {
                phase = AssistPhase.LOST
                AssistOutput(sweep(), phase)
            }
        }
    }

    // ------------------------------------------------------------------------ shared

    private fun resetCorrection() {
        lastCorrectionSide = 0
        sameSideStreak = 0
    }

    private fun straight(speed: Int) = MotorPair(clamp(speed), clamp(speed))

    /** Pivot back toward the surface, using the last correction as the best available hint. */
    private fun sweep(): MotorPair {
        val side = if (lastCorrectionSide == 0) 1 else lastCorrectionSide
        return if (side > 0) {
            MotorPair(clamp(searchSpeed), clamp(-searchSpeed))
        } else {
            MotorPair(clamp(-searchSpeed), clamp(searchSpeed))
        }
    }

    /** Keeps a non-zero demand above the gearbox deadband and inside the firmware clamp. */
    private fun clamp(speed: Int): Int = when {
        speed == 0 -> 0
        speed > 0 -> speed.coerceIn(DriveMixer.DEADBAND_FLOOR, DriveMixer.MAX_SPEED)
        else -> speed.coerceIn(-DriveMixer.MAX_SPEED, -DriveMixer.DEADBAND_FLOOR)
    }

    private companion object {
        /** Added to the correction for each extra frame spent on the same boundary. */
        const val CORRECTION_STEP = 8
    }
}

/**
 * Watches raw `?LINE` codes for the car running square across a white line.
 *
 * On a closed circuit that only happens at the chequered start/finish, which makes it a
 * free lap trigger — no beacon, no button, no thumb on a stopwatch. The chequer's
 * alternating squares make the sensors chatter, so a crossing latches and will not
 * re-arm until the car has seen [rearmFrames] consecutive clean surface readings.
 */
class MarkerDetector(private val rearmFrames: Int = 8) {

    private var latched = false
    private var cleanStreak = 0

    /** @return true exactly once per crossing, on its leading edge. */
    fun onCode(code: Int): Boolean {
        val reading = SensorPair.from(code)
        if (reading == SensorPair.BOTH_WHITE) {
            cleanStreak = 0
            if (latched) return false
            latched = true
            return true
        }

        if (reading == SensorPair.BOTH_DARK) {
            cleanStreak++
            if (cleanStreak >= rearmFrames) latched = false
        } else {
            // One sensor on a boundary is a normal drift, neither a crossing nor a re-arm.
            cleanStreak = 0
        }
        return false
    }

    fun reset() {
        latched = false
        cleanStreak = 0
    }
}
