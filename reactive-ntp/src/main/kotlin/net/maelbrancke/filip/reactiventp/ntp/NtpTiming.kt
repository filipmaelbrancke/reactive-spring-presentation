package net.maelbrancke.filip.reactiventp.ntp

/**
 * roundtrip delay (ms)
 * local clock offset (ms) :: negative if device's clock is ahead / positive if device's clock is behind
 */
data class NtpTiming(val delay: Long, val localClockOffset: Long)