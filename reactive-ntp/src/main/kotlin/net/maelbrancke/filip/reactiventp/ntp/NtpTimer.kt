package net.maelbrancke.filip.reactiventp.ntp

import org.apache.commons.net.ntp.NTPUDPClient
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

typealias NtpPoolHostName = String

@Service
class NtpTimer(private val sntpClient: SNTPClient) {

    companion object {

        const val DEFAULT_NTP_POOL = "pool.ntp.org"

    }

    private var initialized = false
    private val retryCount = 50
    private val connectionTimeout = 10_000
    private var timingInfo: NtpTiming? = null

    fun initialize(ntpPoolAddress: NtpPoolHostName = DEFAULT_NTP_POOL): Mono<Time> {
        return if (initialized) {
            Mono.just(calculateNow())
        } else {
            doNtpMeasurement(ntpPoolAddress)
                    .map { calculateNow() }
        }
    }

    private fun doNtpMeasurement(ntpPoolAddress: NtpPoolHostName): Mono<NtpTiming> {
        return Flux
                .just(ntpPoolAddress)
                .compose(resolveNtpPoolAddressToIpAddresses())
                .compose(performNtpAlgorithm())
                .doOnNext { timing -> System.out.println("Ntp measurement:: $timing") }
                .next()
    }

    private fun resolveNtpPoolAddressToIpAddresses(): (Flux<NtpPoolHostName>) -> Flux<InetAddress> = { ntpPoolAddressFlux ->
        ntpPoolAddressFlux
                .publishOn(Schedulers.parallel())
                .flatMap { ntpPoolAddress ->
                    try {
                        InetAddress.getAllByName(ntpPoolAddress).toFlux()
                    } catch (e: UnknownHostException) {
                        e.toFlux<InetAddress>()
                    }
                }
    }

    private fun performNtpAlgorithm(): (Flux<InetAddress>) -> Mono<NtpTiming> = { ipAddresses ->
        ipAddresses
                .flatMap(bestResponseAgainstSingleIp(5))
                .take(5)
                .collectList()
                .filter { ntpTimings -> ntpTimings.isNotEmpty() }
                .map(filterMedian())
                .doOnNext { ntpTiming ->
                    cacheTimingInfo(ntpTiming)
                }
    }

    private fun bestResponseAgainstSingleIp(repeatCount: Int): (InetAddress) -> Flux<NtpTiming> = { singleIp ->
        Flux
                .just(singleIp)
                .repeat(repeatCount.toLong())
                .flatMap { ip ->

                    doSNTPRequest(ip)
                            .subscribeOn(Schedulers.parallel())
                            .doOnError { error: Throwable? -> System.out.println("Error requesting time : $error") }
                            .retry(retryCount.toLong())

                }
                .collectList()
                .map(getTimingWithFastestRoundTrip())
                .flux()
    }

    private fun doSNTPRequest(ip: InetAddress): Flux<NtpTiming> {
        return Flux.create<NtpTiming>({ sink ->
            System.out.println("requesting time from $ip")
            try {
                sink.next(requestTime(ip))
                sink.complete()
            } catch (e: IOException) {
                sink.error(e)
            }
        },
                FluxSink.OverflowStrategy.BUFFER)
    }

    private fun getTimingWithFastestRoundTrip(): (List<NtpTiming>) -> NtpTiming = { ntpTimings ->
        val fastestNtpMeasurement = ntpTimings
                .asSequence()
                .sortedBy(NtpTiming::delay)
                .first()
        fastestNtpMeasurement
    }

    private fun filterMedian(): (List<NtpTiming>) -> NtpTiming = { ntpTimings ->
        val sortedByOffset = ntpTimings
                .sortedBy(NtpTiming::localClockOffset)

        sortedByOffset[sortedByOffset.size / 2]
        /* ntpTimings
                .asSequence()
                .sortedBy(NtpTiming::delay)
                .medianBy() */

    }

    private fun cacheTimingInfo(ntpTiming: NtpTiming) {
        System.out.println("Ntp timing performed: $ntpTiming")
        timingInfo = ntpTiming
        initialized = true
    }

    private fun calculateNow(): Time {
        val currentTime = System.currentTimeMillis()
        val offset = timingInfo?.localClockOffset ?: 0
        val ntpTime = System.currentTimeMillis() + offset
        val deviation = timingInfo?.delay ?: 0
        return Time(timeInMillis = currentTime, ntpTimeInMillis = ntpTime, deviation = deviation)
    }

    private fun requestTime(ipAddress: InetAddress): NtpTiming {
        System.out.println("Requesting SNTP time from $ipAddress")
        val ntpClient = NTPUDPClient()
        ntpClient.defaultTimeout = connectionTimeout
        val timeInfo = ntpClient.getTime(ipAddress)
        timeInfo.computeDetails() // compute offset/delay if not already done

        val ntpTiming = NtpTiming(timeInfo.delay, timeInfo.offset)
        System.out.println("SNTP timing from $ipAddress :: delay = ${ntpTiming.delay} / offset = ${ntpTiming.localClockOffset}")
        return ntpTiming
    }
}