package com.example.cutebotandroidsample.race

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Paces every write to the Nordic UART RX characteristic.
 *
 * CONTEST.md warns that the micro:bit's UART buffer backs up if joystick frames and
 * telemetry queries are interleaved at 10-20ms, producing steering lag and — worse —
 * delayed emergency stops. This queue enforces three rules:
 *
 *  1. **Fixed cadence.** At most one drive write plus one auxiliary write per [tickMs] tick.
 *  2. **Latest-wins drive.** A joystick moving at 60fps overwrites the pending drive slot
 *     instead of queueing 60 stale frames. Identical consecutive frames are suppressed.
 *  3. **Priority bypass.** [sendNow] writes immediately, so `S#` is never stuck behind a
 *     colour change.
 *
 * Telemetry queries round-robin through [telemetryPlan] so a full dashboard costs one
 * query per cycle rather than six at once.
 */
class CommandQueue(
    private val scope: CoroutineScope,
    private val tickMs: Long = 70L,
    private val telemetryEveryTicks: Int = 2,
    private val maxPending: Int = 32,
    var telemetryPlan: List<String> = DEFAULT_TELEMETRY_PLAN,
    private val send: (String) -> Unit,
) {
    private val pendingDrive = AtomicReference<String?>(null)
    private val discrete = ArrayDeque<String>()
    private val discreteLock = Any()

    private var lastDrive: String? = null
    private var tick = 0L
    private var telemetryCursor = 0
    private var pump: Job? = null

    /** Writes actually put on the wire. */
    @Volatile
    var sentCount: Long = 0L
        private set

    /** Drive frames superseded before they were sent, plus overflow drops. */
    @Volatile
    var coalescedCount: Long = 0L
        private set

    var telemetryEnabled: Boolean = true

    fun start() {
        if (pump?.isActive == true) return
        pump = scope.launch {
            while (isActive) {
                delay(tickMs)
                pumpOnce()
            }
        }
    }

    fun stop() {
        pump?.cancel()
        pump = null
    }

    /** Replaces the pending drive frame. Safe to call on every input event. */
    fun setDrive(command: String) {
        if (pendingDrive.getAndSet(command) != null) coalescedCount++
    }

    /** Queues a non-critical command (lights, sound, display) behind the drive channel. */
    fun enqueue(command: String) {
        synchronized(discreteLock) {
            while (discrete.size >= maxPending) {
                discrete.removeFirst()
                coalescedCount++
            }
            discrete.addLast(command)
        }
    }

    /** Bypasses pacing entirely — reserved for the emergency stop. */
    fun sendNow(command: String) {
        if (command == "S") {
            // A stop invalidates any queued motion, including the frame we were about to send.
            pendingDrive.set(null)
            lastDrive = "S"
        }
        sentCount++
        send(command)
    }

    /** Forgets cached state so a reconnect re-sends the current drive frame. */
    fun reset() {
        pendingDrive.set(null)
        synchronized(discreteLock) { discrete.clear() }
        lastDrive = null
        tick = 0
        telemetryCursor = 0
    }

    internal fun pumpOnce() {
        tick++

        val drive = pendingDrive.getAndSet(null)
        if (drive != null && drive != lastDrive) {
            lastDrive = drive
            sentCount++
            send(drive)
        }

        val queued = synchronized(discreteLock) { discrete.removeFirstOrNull() }
        if (queued != null) {
            sentCount++
            send(queued)
            return
        }

        if (telemetryEnabled && telemetryPlan.isNotEmpty() && tick % telemetryEveryTicks == 0L) {
            val query = telemetryPlan[(telemetryCursor++ % telemetryPlan.size + telemetryPlan.size) % telemetryPlan.size]
            sentCount++
            send(query)
        }
    }

    companion object {
        /**
         * Line sensor is polled twice per cycle because it feeds the autonomous follower;
         * the dashboard-only sensors share the remaining slots.
         */
        val DEFAULT_TELEMETRY_PLAN = listOf("?LINE", "?DIST", "?LINE", "?ACCEL", "PING", "?LIGHT", "?LINE", "?TEMP")

        /** Minimal plan used while the line follower is driving. */
        val LINE_FOCUSED_PLAN = listOf("?LINE", "?LINE", "?DIST", "?LINE", "PING")
    }
}
