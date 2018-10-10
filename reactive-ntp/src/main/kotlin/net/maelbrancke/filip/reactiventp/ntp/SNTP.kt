package net.maelbrancke.filip.reactiventp.ntp

import org.apache.commons.net.ntp.NTPUDPClient
import java.net.InetAddress

/**
 * Simple Ntp client for retrieving network time.
 * Uses Apache Commons Net
 * Does not currently use shared socket client, as the
 * performance improvements seem negligible after some initial testing (@see NTPUDPClient)
 * https://tools.ietf.org/html/rfc1361
 */
class SNTP {

    companion object {
        private val INSTANCE = SNTP()

        fun create(): SNTP {
            return INSTANCE
        }
    }

    fun requestTime(ipAddress: InetAddress, timeout: Int): NtpTiming {
        System.out.println("Requesting SNTP time from $ipAddress")
        val ntpClient = NTPUDPClient()
        ntpClient.defaultTimeout = timeout
        val timeInfo = ntpClient.getTime(ipAddress)
        timeInfo.computeDetails()

        val ntpTiming = NtpTiming(timeInfo.delay, timeInfo.offset)
        ntpClient.close()
        System.out.println("SNTP timing from $ipAddress :: delay = ${ntpTiming.delay} / offset = ${ntpTiming.localClockOffset}")
        return ntpTiming
    }
}