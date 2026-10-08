package com.example.cutebotandroidsample.race

import android.content.Context

/**
 * Tiny persistence layer for the settings a racer does not want to retype between runs:
 * the robot's MAC, the motor trim they spent Thursday dialling in, their speed cap and
 * their team name.
 */
class RacePrefs(context: Context) {

    private val prefs = context.getSharedPreferences("race_prefs", Context.MODE_PRIVATE)

    /**
     * Blank is never stored or returned: clearing the field to retype a MAC must not be
     * able to leave the app with no robot to connect to on race morning.
     */
    var address: String
        get() = prefs.getString(KEY_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_ADDRESS
        set(value) {
            if (value.isNotBlank()) prefs.edit().putString(KEY_ADDRESS, value.trim()).apply()
        }

    var trim: Int
        get() = prefs.getInt(KEY_TRIM, 0)
        set(value) = prefs.edit().putInt(KEY_TRIM, value).apply()

    var speedCap: Int
        get() = prefs.getInt(KEY_SPEED_CAP, 70)
        set(value) = prefs.edit().putInt(KEY_SPEED_CAP, value).apply()

    var teamName: String
        get() = prefs.getString(KEY_TEAM, DEFAULT_TEAM) ?: DEFAULT_TEAM
        set(value) = prefs.edit().putString(KEY_TEAM, value).apply()

    var tiltMode: Boolean
        get() = prefs.getBoolean(KEY_TILT, false)
        set(value) = prefs.edit().putBoolean(KEY_TILT, value).apply()

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_TRIM = "trim"
        const val KEY_SPEED_CAP = "speed_cap"
        const val KEY_TEAM = "team"
        const val KEY_TILT = "tilt"

        /** Our test robot, so a fresh install can connect without retyping the MAC. */
        const val DEFAULT_ADDRESS = "D3:9C:7F:A3:99:1F"
        const val DEFAULT_TEAM = "NEXT APP"
    }
}
