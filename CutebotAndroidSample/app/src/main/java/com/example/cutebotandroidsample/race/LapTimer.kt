package com.example.cutebotandroidsample.race

/** Immutable snapshot of the stopwatch, suitable for driving Compose state. */
data class LapTimerState(
    val running: Boolean = false,
    val elapsedMs: Long = 0L,
    val laps: List<Long> = emptyList(),
) {
    val bestLapMs: Long? get() = laps.minOrNull()
    val lastLapMs: Long? get() = laps.lastOrNull()
    val currentLapMs: Long get() = elapsedMs - laps.sum()
}

/**
 * Stopwatch for practice runs. Only one timed attempt counts at the finals, so the point
 * of this is Wednesday-to-Friday tuning: split laps, keep the session's best, and see
 * whether a trim or speed change actually helped.
 *
 * Lap boundaries are stored as offsets into *elapsed* time rather than wall-clock stamps,
 * so pausing and resuming the timer cannot corrupt the current lap. Time is injected to
 * keep the behaviour unit-testable.
 */
class LapTimer(private val now: () -> Long = System::currentTimeMillis) {

    /** Wall clock origin, rebased on each resume so `now() - origin` is always elapsed. */
    private var origin: Long = 0L
    private var frozenElapsed: Long = 0L
    private var running = false

    /** Elapsed-time offsets at which a lap was completed. */
    private val lapMarks = mutableListOf<Long>()

    fun start() {
        if (running) return
        origin = now() - frozenElapsed
        running = true
    }

    fun stop() {
        if (!running) return
        frozenElapsed = elapsed()
        running = false
    }

    /** Records the current lap and immediately begins the next one. */
    fun lap() {
        if (!running) return
        lapMarks += elapsed()
    }

    fun reset() {
        running = false
        origin = 0L
        frozenElapsed = 0L
        lapMarks.clear()
    }

    fun snapshot(): LapTimerState = LapTimerState(
        running = running,
        elapsedMs = elapsed(),
        laps = lapDurations(),
    )

    private fun elapsed(): Long = if (running) now() - origin else frozenElapsed

    /** Converts cumulative marks into per-lap durations. */
    private fun lapDurations(): List<Long> {
        var previous = 0L
        return lapMarks.map { mark ->
            val duration = mark - previous
            previous = mark
            duration
        }
    }
}

/** Formats a duration as `m:ss.cc`, the shape racers read at a glance. */
fun formatLapTime(ms: Long): String {
    val totalCs = ms.coerceAtLeast(0L) / 10
    val minutes = totalCs / 6000
    val seconds = (totalCs / 100) % 60
    val centis = totalCs % 100
    return if (minutes > 0) {
        "%d:%02d.%02d".format(minutes, seconds, centis)
    } else {
        "%d.%02d".format(seconds, centis)
    }
}
