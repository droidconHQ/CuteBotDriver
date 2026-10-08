package com.example.cutebotandroidsample.race

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Robot Rally mat is a dark racing surface bounded by white lines, so "both sensors
 * dark" is the healthy state and a white reading means a boundary has been touched.
 */
class TrackAssistEdgeAvoidTest {

    private fun assist() = TrackAssist(mode = TrackMode.EDGE_AVOID, baseSpeed = 55, correction = 20)

    @Test
    fun `both sensors on the surface drives straight at base speed`() {
        val output = assist().onLine(SensorPair.BOTH_DARK.code)
        assertEquals(MotorPair(55, 55), output.motors)
        assertEquals(AssistPhase.ON_TRACK, output.phase)
    }

    @Test
    fun `left sensor hitting a boundary steers right`() {
        // Code 1 = right sensor dark, so the LEFT sensor is the one on white.
        val output = assist().onLine(SensorPair.RIGHT_DARK.code)
        assertTrue("expected a right-hand correction, got ${output.motors}", output.motors.left > output.motors.right)
        assertEquals(AssistPhase.CORRECTING, output.phase)
    }

    @Test
    fun `right sensor hitting a boundary steers left`() {
        val output = assist().onLine(SensorPair.LEFT_DARK.code)
        assertTrue("expected a left-hand correction, got ${output.motors}", output.motors.right > output.motors.left)
        assertEquals(AssistPhase.CORRECTING, output.phase)
    }

    @Test
    fun `holding the same boundary escalates the correction`() {
        val assist = assist()
        val first = assist.onLine(SensorPair.RIGHT_DARK.code).motors
        val second = assist.onLine(SensorPair.RIGHT_DARK.code).motors
        val third = assist.onLine(SensorPair.RIGHT_DARK.code).motors
        val spread = { pair: MotorPair -> pair.left - pair.right }
        assertTrue("correction should grow while the boundary persists",
            spread(third) > spread(second) && spread(second) > spread(first))
    }

    @Test
    fun `escalation resets once the car is back on the surface`() {
        val assist = assist()
        repeat(4) { assist.onLine(SensorPair.RIGHT_DARK.code) }
        assist.onLine(SensorPair.BOTH_DARK.code)
        val afterRecovery = assist.onLine(SensorPair.RIGHT_DARK.code).motors
        val fresh = assist().onLine(SensorPair.RIGHT_DARK.code).motors
        assertEquals(fresh, afterRecovery)
    }

    @Test
    fun `crossing the chequer raises a marker exactly once`() {
        val assist = assist()
        assist.onLine(SensorPair.BOTH_DARK.code)
        val entering = assist.onLine(SensorPair.BOTH_WHITE.code)
        val stillOnIt = assist.onLine(SensorPair.BOTH_WHITE.code)
        assertTrue("leading edge should report a crossing", entering.markerCrossed)
        assertFalse("a sustained crossing must not double-count", stillOnIt.markerCrossed)
        assertEquals(AssistPhase.MARKER, entering.phase)
    }

    @Test
    fun `a sustained white reading eventually starts searching`() {
        val assist = assist()
        repeat(10) { assist.onLine(SensorPair.BOTH_WHITE.code) }
        assertEquals(AssistPhase.LOST, assist.phase)
    }

    @Test
    fun `searching pivots back toward the last correction side`() {
        val assist = assist()
        assist.onLine(SensorPair.LEFT_DARK.code) // correction to the left
        repeat(10) { assist.onLine(SensorPair.BOTH_WHITE.code) }
        val sweep = assist.onLine(SensorPair.BOTH_WHITE.code).motors
        assertTrue("expected a left pivot, got $sweep", sweep.right > sweep.left)
    }

    @Test
    fun `every output respects the gearbox deadband`() {
        val assist = assist()
        val readings = listOf(0, 1, 2, 3, 0, 0, 0, 0, 1, 3)
        readings.forEach { code ->
            val motors = assist.onLine(code).motors
            listOf(motors.left, motors.right).forEach { speed ->
                assertTrue(
                    "speed $speed from code $code sits inside the deadband",
                    speed == 0 || kotlin.math.abs(speed) >= DriveMixer.DEADBAND_FLOOR,
                )
            }
        }
    }
}

/** The classic arrangement: a dark line on a light floor, tracked rather than avoided. */
class TrackAssistLineFollowTest {

    private fun assist() = TrackAssist(mode = TrackMode.LINE_FOLLOW, baseSpeed = 50, correction = 20)

    @Test
    fun `right sensor on the line steers right`() {
        val output = assist().onLine(SensorPair.RIGHT_DARK.code)
        assertTrue(output.motors.left > output.motors.right)
    }

    @Test
    fun `a brief white reading is treated as centred on a narrow line`() {
        val output = assist().onLine(SensorPair.BOTH_WHITE.code)
        assertEquals(MotorPair(50, 50), output.motors)
        assertEquals(AssistPhase.ON_TRACK, output.phase)
    }

    @Test
    fun `a persistent white reading means the line is lost`() {
        val assist = assist()
        repeat(8) { assist.onLine(SensorPair.BOTH_WHITE.code) }
        assertEquals(AssistPhase.LOST, assist.phase)
    }
}

class MarkerDetectorTest {

    @Test
    fun `a crossing is reported once and re-arms after clean surface`() {
        val detector = MarkerDetector(rearmFrames = 3)
        assertTrue(detector.onCode(SensorPair.BOTH_WHITE.code))
        assertFalse(detector.onCode(SensorPair.BOTH_WHITE.code))

        // The chequer's squares make the sensors chatter; that must not re-arm the latch.
        assertFalse(detector.onCode(SensorPair.LEFT_DARK.code))
        assertFalse(detector.onCode(SensorPair.BOTH_WHITE.code))

        repeat(3) { detector.onCode(SensorPair.BOTH_DARK.code) }
        assertTrue("a full lap later, the next crossing counts", detector.onCode(SensorPair.BOTH_WHITE.code))
    }

    @Test
    fun `boundary drift alone never counts as a lap`() {
        val detector = MarkerDetector()
        val drift = listOf(3, 1, 3, 2, 3, 1, 1, 3, 2, 3)
        assertTrue(drift.none { detector.onCode(it) })
    }
}
