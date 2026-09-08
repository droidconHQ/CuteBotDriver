package com.example.cutebotandroidsample

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TelemetryParsingTest {

    @Before
    fun setUp() {
        CutebotController.resetBuffer()
        CutebotController.onTelemetry = null
    }

    @Test
    fun testDistanceTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("DIST:42#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.Distance)
        assertEquals(42, (received as CutebotTelemetry.Distance).cm)
    }

    @Test
    fun testLineTelemetry() {
        val lineCodes = listOf(
            0 to "White (0)",
            1 to "Right Black (1)",
            2 to "Left Black (2)",
            3 to "Both Black (3)"
        )

        for ((code, expectedDesc) in lineCodes) {
            var received: CutebotTelemetry? = null
            CutebotController.onTelemetry = { received = it }

            CutebotController.handleNotification("LINE:$code#\n".toByteArray(Charsets.UTF_8))

            assertTrue(received is CutebotTelemetry.LineTracker)
            val line = received as CutebotTelemetry.LineTracker
            assertEquals(code, line.code)
            assertEquals(expectedDesc, line.description)
        }
    }

    @Test
    fun testCompassTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("COMPASS:270#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.Compass)
        assertEquals(270, (received as CutebotTelemetry.Compass).degrees)
    }

    @Test
    fun testAccelerationTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("ACCEL:12,-45,1024#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.Acceleration)
        val accel = received as CutebotTelemetry.Acceleration
        assertEquals(12, accel.x)
        assertEquals(-45, accel.y)
        assertEquals(1024, accel.z)
    }

    @Test
    fun testLightLevelTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("LIGHT:185#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.LightLevel)
        assertEquals(185, (received as CutebotTelemetry.LightLevel).level)
    }

    @Test
    fun testTemperatureTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("TEMP:26#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.Temperature)
        assertEquals(26, (received as CutebotTelemetry.Temperature).celsius)
    }

    @Test
    fun testPongTelemetry() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        CutebotController.handleNotification("PONG#\n".toByteArray(Charsets.UTF_8))

        assertTrue(received is CutebotTelemetry.Pong)
    }

    @Test
    fun testFragmentedPackets() {
        var received: CutebotTelemetry? = null
        CutebotController.onTelemetry = { received = it }

        // Chunk 1: Incomplete packet
        CutebotController.handleNotification("DIST:1".toByteArray(Charsets.UTF_8))
        assertNull(received)

        // Chunk 2: Completion of packet
        CutebotController.handleNotification("5#\n".toByteArray(Charsets.UTF_8))
        assertTrue(received is CutebotTelemetry.Distance)
        assertEquals(15, (received as CutebotTelemetry.Distance).cm)
    }

    @Test
    fun testMultiplePacketsInSingleChunk() {
        val list = mutableListOf<CutebotTelemetry>()
        CutebotController.onTelemetry = { list.add(it) }

        CutebotController.handleNotification("DIST:30#\nTEMP:22#\n".toByteArray(Charsets.UTF_8))

        assertEquals(2, list.size)
        assertTrue(list[0] is CutebotTelemetry.Distance)
        assertEquals(30, (list[0] as CutebotTelemetry.Distance).cm)
        assertTrue(list[1] is CutebotTelemetry.Temperature)
        assertEquals(22, (list[1] as CutebotTelemetry.Temperature).celsius)
    }
}
