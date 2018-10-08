package net.maelbrancke.filip.reactiventp.ntp

/**
 * timeInMillis = number of milliseconds since device boot (SystemClock.elapsedRealtime)
 * (clock does not stop when system enters deep sleep - guaranteed to be monotonic)
 *
 * ntpTimeInMillis =
 *
 * deviation =
 */
data class Time(val timeInMillis: Long, val ntpTimeInMillis: Long, val deviation: Long)