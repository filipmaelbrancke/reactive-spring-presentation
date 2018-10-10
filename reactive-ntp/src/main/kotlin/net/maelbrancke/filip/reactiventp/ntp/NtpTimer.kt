package net.maelbrancke.filip.reactiventp.ntp

import org.apache.commons.net.ntp.NTPUDPClient
import org.nield.kotlinstatistics.median
import org.nield.kotlinstatistics.medianBy
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

typealias NtpPoolHostName = String

class NtpTimer {

    companion object {
        const val DEFAULT_NTP_POOL = "pool.ntp.org"

        private val INSTANCE = NtpTimer()

        fun build(): NtpTimer {
            return INSTANCE
        }
    }

    private var initialized = false

    private val retryCount = 50
    private val connectionTimeout = 10_000
    private var timingInfo: NtpTiming? = null

    fun initialize(ntpPoolAddress: NtpPoolHostName = DEFAULT_NTP_POOL): Mono<Time> {
        return if (initialized) {
            Mono.just(calculateNow())
        } else {
            initializeNtp(ntpPoolAddress)
                    .map { calculateNow() }
        }
    }

    private fun initializeNtp(ntpPoolAddress: NtpPoolHostName): Mono<NtpTiming> {
        return Flux
                .just(ntpPoolAddress)
                .compose(resolveNtpPoolAddressToIpAddresses())
                .compose(performNtpAlgorithm())
                .doOnNext { t -> System.out.println("initialized:: $t") }
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

                    Flux.create<NtpTiming>({ sink ->
                        System.out.println("requesting time from $ip")
                        try {
                            sink.next(requestTime(ip))
                            sink.complete()
                        } catch (e:IOException) {
                            sink.error(e)
                        }
                    },
                            FluxSink.OverflowStrategy.BUFFER)
                            .subscribeOn(Schedulers.parallel())
                            .doOnError { error: Throwable? -> System.out.println("Error requesting time : $error") }
                            .retry(retryCount.toLong())

                }
                .collectList()
                .map(getTimingWithFastestRoundTrip())
                .flux()
    }

    private fun getTimingWithFastestRoundTrip(): (List<NtpTiming>) -> NtpTiming = { ntpTimings ->
        val fastestNtpMeasurement = ntpTimings
                .asSequence()
                .sortedBy(NtpTiming::delay)
                .first()
        fastestNtpMeasurement
    }

    private fun filterMedian(): (List<NtpTiming>) -> NtpTiming = { ntpTimings ->
        val sortedByDuration = ntpTimings
                .sortedBy(NtpTiming::delay)

        sortedByDuration[sortedByDuration.size / 2]
        /*sequenceOf(1.0, 3.0, 5.0).median()

        sort by clock offset and pick median?

        ntpTimings
                .asSequence()
                .sortedBy(NtpTiming::delay)
                .medianBy()*/

    }

    private fun cacheTimingInfo(ntpTiming: NtpTiming) {
        System.out.println("Ntp timing performed: $ntpTiming")
        timingInfo = ntpTiming
        //isInitialized.onNext(true)
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