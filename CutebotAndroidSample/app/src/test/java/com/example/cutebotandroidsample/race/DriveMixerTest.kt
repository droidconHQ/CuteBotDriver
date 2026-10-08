package com.example.cutebotandroidsample.race

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DriveMixerTest {

    @Test
    fun `centred stick produces a full stop`() {
        assertEquals(MotorPair.STOPPED, DriveMixer.mix(throttle = 0f, steer = 0f, speedCap = 80))
    }

    @Test
    fun `jitter inside the deadzone is ignored`() {
        assertEquals(MotorPair.STOPPED, DriveMixer.mix(throttle = 0.05f, steer = -0.04f, speedCap = 80))
    }

    @Test
    fun `a nudge clears the gearbox deadband instead of humming`() {
        // Just past the stick deadzone: the wheels must actually turn.
        val result = DriveMixer.mix(throttle = 0.12f, steer = 0f, speedCap = 80)
        assertTrue(
            "expected both wheels at or above ${DriveMixer.DEADBAND_FLOOR}, got $result",
            result.left >= DriveMixer.DEADBAND_FLOOR && result.right >= DriveMixer.DEADBAND_FLOOR,
        )
    }

    @Test
    fun `full throttle reaches the speed cap and no further`() {
        val result = DriveMixer.mix(throttle = 1f, steer = 0f, speedCap = 70)
        assertEquals(MotorPair(70, 70), result)
    }

    @Test
    fun `reverse mirrors forward`() {
        val forward = DriveMixer.mix(throttle = 1f, steer = 0f, speedCap = 70)
        val backward = DriveMixer.mix(throttle = -1f, steer = 0f, speedCap = 70)
        assertEquals(MotorPair(-forward.left, -forward.right), backward)
    }

    @Test
    fun `steering right slows the right wheel`() {
        val result = DriveMixer.mix(throttle = 0.8f, steer = 0.5f, speedCap = 90)
        assertTrue("expected left > right, got $result", result.left > result.right)
    }

    @Test
    fun `steer with no throttle pivots in place`() {
        val result = DriveMixer.mix(throttle = 0f, steer = 1f, speedCap = 80)
        assertEquals(80, result.left)
        assertEquals(-80, result.right)
    }

    @Test
    fun `a full-lock turn stays inside the firmware clamp`() {
        val result = DriveMixer.mix(throttle = 1f, steer = 1f, speedCap = 100)
        assertTrue(abs(result.left) <= DriveMixer.MAX_SPEED)
        assertTrue(abs(result.right) <= DriveMixer.MAX_SPEED)
    }

    @Test
    fun `trim biases the wheels in opposite directions`() {
        val untrimmed = DriveMixer.mix(throttle = 0.6f, steer = 0f, speedCap = 80)
        val trimmed = DriveMixer.mix(throttle = 0.6f, steer = 0f, speedCap = 80, trim = 10)
        assertEquals(untrimmed.left + 10, trimmed.left)
        assertEquals(untrimmed.right - 10, trimmed.right)
    }

    @Test
    fun `speed cap below the deadband floor is raised to it`() {
        val result = DriveMixer.mix(throttle = 1f, steer = 0f, speedCap = 5)
        assertEquals(MotorPair(DriveMixer.DEADBAND_FLOOR, DriveMixer.DEADBAND_FLOOR), result)
    }

    @Test
    fun `out of range input is clamped rather than amplified`() {
        val result = DriveMixer.mix(throttle = 4f, steer = 0f, speedCap = 60)
        assertEquals(MotorPair(60, 60), result)
    }
}
