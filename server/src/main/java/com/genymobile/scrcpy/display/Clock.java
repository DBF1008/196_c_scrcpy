package com.genymobile.scrcpy.display;

/**
 * Abstraction for a monotonic clock, used to decouple components from {@code android.os.SystemClock} for testability.
 */
public interface Clock {
    /**
     * Return the current time in milliseconds (monotonic).
     *
     * @return the current uptime in milliseconds
     */
    long uptimeMillis();
}
