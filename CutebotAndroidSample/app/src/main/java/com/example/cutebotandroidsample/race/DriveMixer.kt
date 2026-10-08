package com.example.cutebotandroidsample.race

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

/** A left/right motor speed pair in the firmware's -100..100 domain. */
data class MotorPair(val left: Int, val right: Int) {
    /** The `MS,left,right` payload understood by the Cutebot firmware. */
    fun toCommand(): String = "MS,$left,$right"

    companion object {
        val STOPPED = MotorPair(0, 0)
    }
}

/**
 * Converts stick/tilt input into differential motor speeds.
 *
 * Two hardware realities drive the maths here:
 *  - CONTEST.md documents a gearbox "deadband": anything in 1..20 hums without turning the
 *    wheels, so the usable floor is ~25. We therefore map a non-zero stick onto
 *    [DEADBAND_FLOOR, speedCap] rather than [0, speedCap] — a nudge of the stick produces
 *    actual motion instead of a buzz.
 *  - The DC motors are un-encoded and vary between units, so a persistent [trim] bias is
 *    applied last to straighten out a robot that pulls to one side.
 */
object DriveMixer {

    /** Lowest motor value that reliably overcomes gearbox friction. */
    const val DEADBAND_FLOOR = 25

    /** Firmware clamp for `ML` / `MR` / `MS`. */
    const val MAX_SPEED = 100

    /**
     * Arcade mixing: one stick supplies both axes.
     *
     * @param throttle forward/back, -1f..1f (positive = forward)
     * @param steer left/right, -1f..1f (positive = steer right)
     * @param speedCap upper motor bound, honoured after deadband expansion
     * @param trim static bias added to the left motor and removed from the right
     * @param stickDeadzone input magnitude treated as centre, rejecting thumb jitter
     */
    fun mix(
        throttle: Float,
        steer: Float,
        speedCap: Int,
        trim: Int = 0,
        stickDeadzone: Float = 0.08f,
    ): MotorPair {
        val cap = speedCap.coerceIn(DEADBAND_FLOOR, MAX_SPEED)
        val t = squash(throttle, stickDeadzone)
        val s = squash(steer, stickDeadzone)

        if (t == 0f && s == 0f) return MotorPair.STOPPED

        // Arcade sum, then normalise so a full-throttle turn keeps its left/right ratio
        // instead of clipping the outside wheel.
        var left = t + s
        var right = t - s
        val peak = max(1f, max(abs(left), abs(right)))
        left /= peak
        right /= peak

        return MotorPair(
            left = expand(left, cap, trim),
            right = expand(right, cap, -trim),
        )
    }

    /** Applies the centre deadzone and rescales the remaining travel back to a full 0..1. */
    private fun squash(value: Float, deadzone: Float): Float {
        val v = value.coerceIn(-1f, 1f)
        if (abs(v) <= deadzone) return 0f
        val scaled = (abs(v) - deadzone) / (1f - deadzone)
        return sign(v) * scaled
    }

    /** Maps a normalised -1..1 wheel demand onto the motor's usable band. */
    private fun expand(normalised: Float, cap: Int, bias: Int): Int {
        if (normalised == 0f) return 0
        val span = cap - DEADBAND_FLOOR
        val magnitude = DEADBAND_FLOOR + span * abs(normalised)
        val signed = sign(normalised) * magnitude + bias
        return signed.toInt().coerceIn(-MAX_SPEED, MAX_SPEED)
    }
}
