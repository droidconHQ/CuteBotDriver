package com.example.cutebotandroidsample.race

import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The queue is the piece standing between a 60fps joystick and a micro:bit with a small
 * UART buffer, so these tests pin the pacing guarantees rather than the plumbing.
 */
class CommandQueueTest {

    private val sent = mutableListOf<String>()

    private fun queue(
        telemetryEveryTicks: Int = 2,
        plan: List<String> = listOf("?LINE", "?DIST"),
    ) = CommandQueue(
        scope = TestScope(),
        telemetryEveryTicks = telemetryEveryTicks,
        telemetryPlan = plan,
        send = { sent += it },
    )

    @Test
    fun `only the newest drive frame survives a tick`() {
        val queue = queue()
        queue.telemetryEnabled = false

        queue.setDrive("MS,30,30")
        queue.setDrive("MS,50,50")
        queue.setDrive("MS,70,70")
        queue.pumpOnce()

        assertEquals(listOf("MS,70,70"), sent)
        assertEquals("two superseded frames should be counted", 2L, queue.coalescedCount)
    }

    @Test
    fun `an unchanged drive frame is not resent`() {
        val queue = queue()
        queue.telemetryEnabled = false

        queue.setDrive("MS,60,60")
        queue.pumpOnce()
        queue.setDrive("MS,60,60")
        queue.pumpOnce()

        assertEquals(listOf("MS,60,60"), sent)
    }

    @Test
    fun `a tick writes at most one drive plus one auxiliary command`() {
        val queue = queue()
        queue.setDrive("MS,40,40")
        repeat(5) { queue.enqueue("HLL,255,0,0") }
        queue.pumpOnce()

        assertEquals(2, sent.size)
    }

    @Test
    fun `emergency stop bypasses the queue and cancels pending motion`() {
        val queue = queue()
        queue.telemetryEnabled = false

        queue.setDrive("MS,90,90")
        queue.sendNow("S")
        queue.pumpOnce()

        assertEquals("the stale throttle frame must not follow the stop", listOf("S"), sent)
    }

    @Test
    fun `telemetry round-robins instead of firing every query at once`() {
        val queue = queue(telemetryEveryTicks = 1, plan = listOf("?LINE", "?DIST"))
        repeat(4) { queue.pumpOnce() }

        assertEquals(listOf("?LINE", "?DIST", "?LINE", "?DIST"), sent)
    }

    @Test
    fun `discrete commands take priority over telemetry`() {
        val queue = queue(telemetryEveryTicks = 1)
        queue.enqueue("HORN")
        queue.pumpOnce()

        assertEquals(listOf("HORN"), sent)
    }

    @Test
    fun `discrete commands drain in order across ticks`() {
        val queue = queue()
        queue.telemetryEnabled = false
        queue.enqueue("ICON,HEART")
        queue.enqueue("DISP,3")
        queue.pumpOnce()
        queue.pumpOnce()

        assertEquals(listOf("ICON,HEART", "DISP,3"), sent)
    }

    @Test
    fun `an overflowing queue drops the oldest rather than growing without bound`() {
        val queue = CommandQueue(
            scope = TestScope(),
            maxPending = 3,
            send = { sent += it },
        )
        queue.telemetryEnabled = false
        listOf("A", "B", "C", "D", "E").forEach(queue::enqueue)
        repeat(5) { queue.pumpOnce() }

        assertEquals(listOf("C", "D", "E"), sent)
        assertTrue(queue.coalescedCount >= 2)
    }

    @Test
    fun `reset forgets the suppression cache so the next frame is resent`() {
        val queue = queue()
        queue.telemetryEnabled = false

        queue.setDrive("MS,60,60")
        queue.pumpOnce()
        queue.reset()
        queue.setDrive("MS,60,60")
        queue.pumpOnce()

        assertEquals(listOf("MS,60,60", "MS,60,60"), sent)
    }
}
