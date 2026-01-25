package com.blackgrapes.kadachabuk

import java.util.concurrent.TimeUnit

/**
 * A utility object for time and duration formatting.
 */
object TimeUtils {

    /**
     * Formats a duration in milliseconds into a user-friendly string like "1h 25m".
     *
     * @param millis The duration in milliseconds.
     * @return A formatted string. If the duration is less than a minute, it returns "< 1m".
     */
    fun formatDuration(millis: Long): String {
        if (millis < 60000) { // Less than 1 minute
            return "< 1m"
        }
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60

        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}