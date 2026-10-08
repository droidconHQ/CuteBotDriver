package com.example.cutebotandroidsample.race

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LapTimerTest {

    /** Controllable clock so the stopwatch can be driven deterministically. */
    private var clock = 0L
    private val timer = LapTimer { clock }

    @Test
    fun `a fresh timer is stopped at zero`() {
        val snapshot = timer.snapshot()
        assertFalse(snapshot.running)
        assertEquals(0L, snapshot.elapsedMs)
        assertNull(snapshot.bestLapMs)
    }

    @Test
    fun `elapsed time advances only while running`() {
        timer.start()
        clock += 5_000
        assertEquals(5_000L, timer.snapshot().elapsedMs)

        timer.stop()
        clock += 10_000
        assertEquals("a stopped clock must not drift", 5_000L, timer.snapshot().elapsedMs)
    }

    @Test
    fun `resuming continues from where it paused`() {
        timer.start()
        clock += 3_000
        timer.stop()
        clock += 60_000 // time spent fiddling with the robot
        timer.start()
        clock += 2_000
        assertEquals(5_000L, timer.snapshot().elapsedMs)
    }

    @Test
    fun `laps are split into individual durations`() {
        timer.start()
        clock += 12_000
        timer.lap()
        clock += 9_500
        timer.lap()

        assertEquals(listOf(12_000L, 9_500L), timer.snapshot().laps)
        assertEquals(9_500L, timer.snapshot().bestLapMs)
        assertEquals(9_500L, timer.snapshot().lastLapMs)
    }

    @Test
    fun `a pause mid-lap does not inflate that lap`() {
        timer.start()
        clock += 4_000
        timer.stop()
        clock += 30_000 // swapping batteries
        timer.start()
        clock += 6_000
        timer.lap()

        assertEquals(listOf(10_000L), timer.snapshot().laps)
    }

    @Test
    fun `current lap excludes completed laps`() {
        timer.start()
        clock += 10_000
        timer.lap()
        clock += 2_500
        assertEquals(2_500L, timer.snapshot().currentLapMs)
    }

    @Test
    fun `lapping a stopped timer does nothing`() {
        timer.lap()
        assertTrue(timer.snapshot().laps.isEmpty())
    }

    @Test
    fun `reset clears everything`() {
        timer.start()
        clock += 8_000
        timer.lap()
        timer.reset()

        val snapshot = timer.snapshot()
        assertFalse(snapshot.running)
        assertEquals(0L, snapshot.elapsedMs)
        assertTrue(snapshot.laps.isEmpty())
    }
}

class LapTimeFormatTest {

    @Test
    fun `sub-minute times omit the minute field`() {
        assertEquals("9.50", formatLapTime(9_500))
        assertEquals("0.07", formatLapTime(70))
    }

    @Test
    fun `times past a minute are zero padded`() {
        assertEquals("1:05.25", formatLapTime(65_250))
        assertEquals("2:00.00", formatLapTime(120_000))
    }

    @Test
    fun `negative input is floored rather than rendered as nonsense`() {
        assertEquals("0.00", formatLapTime(-500))
    }
}
