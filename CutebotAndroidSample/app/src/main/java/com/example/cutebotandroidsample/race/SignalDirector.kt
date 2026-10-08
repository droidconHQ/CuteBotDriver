package com.example.cutebotandroidsample.race

import kotlin.math.abs

/** Everything the signalling layer needs to decide what the robot should be showing. */
data class SignalInputs(
    val motors: MotorPair = MotorPair.STOPPED,
    val connected: Boolean = false,
    val linkStale: Boolean = false,
    val following: Boolean = false,
    val assistPhase: AssistPhase = AssistPhase.ON_TRACK,
)

/**
 * Derives headlight, underglow and display commands from the current drive state.
 *
 * Steering intent is read back out of the *motor pair* rather than the raw stick, so an
 * autonomous line-follower correction lights the same turn signal a thumb would — the
 * judges see the robot signalling its own decisions during a hands-off lap.
 *
 * Every output is latched per channel: a command is only queued when that channel's value
 * actually changes, which keeps a 14Hz drive loop from burying the UART buffer in
 * redundant colour writes.
 */
class SignalDirector(private val enqueue: (String) -> Unit) {

    private val latched = HashMap<String, String>()
    private var blinkOn = false
    private var last = SignalInputs()
    private var reverseBeepArmed = true

    /** Steering deflection beyond which the turn signal engages. */
    private val signalThreshold = 0.22f

    fun update(inputs: SignalInputs) {
        last = inputs
        if (!inputs.connected) {
            latched.clear()
            return
        }
        render(inputs)
    }

    /** Advances the blinker phase; call on a ~300ms cadence. */
    fun blinkTick() {
        blinkOn = !blinkOn
        if (last.connected) render(last)
    }

    /** Clears latches so the next update re-asserts every channel (e.g. after a reconnect). */
    fun reset() {
        latched.clear()
        blinkOn = false
        reverseBeepArmed = true
        last = SignalInputs()
    }

    fun allOff() {
        latched.clear()
        enqueue("HO")
    }

    private fun render(inputs: SignalInputs) {
        val (left, right) = inputs.motors
        val reversing = left < 0 && right < 0
        val moving = left != 0 || right != 0
        val speed = (abs(left) + abs(right)) / 2f

        // -1f (hard left) .. +1f (hard right), inferred from the differential.
        val bias = ((left - right) / (2f * DriveMixer.MAX_SPEED)).coerceIn(-1f, 1f)

        when {
            reversing -> {
                // Reverse lights: steady red both ends, one courtesy beep per manoeuvre.
                emit("HLL", "HLL,255,0,0")
                emit("HLR", "HLR,255,0,0")
                if (reverseBeepArmed) {
                    enqueue("BEEP")
                    reverseBeepArmed = false
                }
            }

            !moving -> {
                // Brake lights while stationary but connected.
                emit("HLL", "HLL,180,0,0")
                emit("HLR", "HLR,180,0,0")
                reverseBeepArmed = true
            }

            abs(bias) >= signalThreshold -> {
                reverseBeepArmed = true
                val amber = if (blinkOn) "255,165,0" else "0,0,0"
                if (bias > 0) {
                    emit("HLR", "HLR,$amber")
                    emit("HLL", "HLL,255,255,255")
                } else {
                    emit("HLL", "HLL,$amber")
                    emit("HLR", "HLR,255,255,255")
                }
            }

            else -> {
                reverseBeepArmed = true
                emit("HLL", "HLL,255,255,255")
                emit("HLR", "HLR,255,255,255")
            }
        }

        emit("UG", underglowFor(inputs, speed, reversing))
    }

    /**
     * Underglow doubles as a status channel. Link health outranks everything (you need to
     * know the radio is struggling before you need to know how fast you are going), then
     * the follower's search state, then a speed gradient.
     */
    private fun underglowFor(inputs: SignalInputs, speed: Float, reversing: Boolean): String = when {
        inputs.linkStale -> if (blinkOn) "UG,255,0,0" else "UG,40,0,0"
        inputs.following && inputs.assistPhase == AssistPhase.LOST ->
            if (blinkOn) "UG,255,0,255" else "UG,60,0,60"
        inputs.following && inputs.assistPhase == AssistPhase.MARKER -> "UG,255,255,255"
        inputs.following -> "UG,0,200,255"
        reversing -> "UG,255,120,0"
        speed <= 0f -> "UG,0,0,60"
        else -> {
            // Green at a crawl through amber to red at full chat, quantised into five
            // buckets so a sweeping throttle produces at most five writes, not fifty.
            val bucket = ((speed / DriveMixer.MAX_SPEED) * 4f).toInt().coerceIn(0, 4)
            when (bucket) {
                0 -> "UG,0,255,80"
                1 -> "UG,120,255,0"
                2 -> "UG,220,220,0"
                3 -> "UG,255,140,0"
                else -> "UG,255,40,0"
            }
        }
    }

    private fun emit(channel: String, command: String) {
        if (latched[channel] == command) return
        latched[channel] = command
        enqueue(command)
    }
}
