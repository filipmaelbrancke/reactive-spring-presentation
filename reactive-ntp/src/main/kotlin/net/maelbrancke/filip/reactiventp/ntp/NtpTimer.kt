package net.maelbrancke.filip.reactiventp.ntp

import org.apache.commons.net.ntp.NTPUDPClient
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.function.Function

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

    fun initialize(ntpPoolAddress: String = DEFAULT_NTP_POOL): Mono<Time> {
        return if (initialized) {
            Mono.just(calculateNow())
        } else {
            initializeNtp(ntpPoolAddress)
                    .map { calculateNow() }
        }
    }

    private fun initializeNtp(ntpPoolAddress: String): Mono<NtpTiming> {
        /*val compose: Mono<InetAddress> = Mono
                .just(ntpPoolAddress)
                .compose(resolveNtpPoolToIpAddresses())*/
        return Flux
                .just(ntpPoolAddress)
                //.compose(resolveNtpPoolToIpAddresses())
                /*.compose { ntpPoolAddressFlux ->
                    ntpPoolAddressFlux
                            .publishOn(Schedulers.parallel())
                            .flatMap { ntpPoolAddress ->
                                try {
                                    InetAddress.getAllByName(ntpPoolAddress).toFlux()
                                } catch (e: UnknownHostException) {
                                    e.toFlux<InetAddress>()
                                }
                            }
                }*/
                //.compose(resolveNtpPoolAddressToIpAddressesFunction)
                .compose(resolveNtpPoolAddressToIpAddresses())

                .doOnNext { t -> System.out.println("initialized:: $t") }
                .next()
                .map { inetAddress -> NtpTiming(1L, 1L) }


                //.compose(performNtpAlgorithm())

                /*.doOnNext { ipAddress -> System.out.println(ipAddress) }
                .map { inetAddress -> NtpTiming(1L, 1L) }*/
    }

    val resolveNtpPoolAddressToIpAddressesFunction = { ntpPoolAddressFlux: Flux<String> ->
        ntpPoolAddressFlux
            .publishOn(Schedulers.parallel())
            .flatMap { ntpPoolAddress ->
                try {
                    InetAddress.getAllByName(ntpPoolAddress).toFlux()
                } catch (e: UnknownHostException) {
                    e.toFlux<InetAddress>()
                }
            } }

    private fun resolveNtpPoolAddressToIpAddresses(): (Flux<String>) -> Flux<InetAddress> = { ntpPoolAddressFlux ->
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

    /*private fun resolveNtpPoolToIpAddresses(): Function<Mono<String>, Flux<InetAddress>> {
        return Function { ntpPool ->
            ntpPool
                    .publishOn(Schedulers.parallel())
                    *//*.flatMapIterable { ntpPoolAddress ->
                        return@flatMapIterable InetAddress.getAllByName(ntpPoolAddress)
                    }*//*
                    .flatMapMany { ntpPoolAddress ->
                        try {
                            InetAddress.getAllByName(ntpPoolAddress).toFlux()
                        } catch (e: UnknownHostException) {
                            e.toFlux<InetAddress>()
                        }
                    }
        }
    }*/

    private fun performNtpAlgorithmShizzle(): (Flux<InetAddress>) -> Mono<NtpTiming> = { ipAddresses ->
        ipAddresses
                .flatMap(bestResponseAgainstSingleIp(5))
                .take(5)
                .collectList()
                .filter { ntpTimings -> ntpTimings.isNotEmpty() }
                .map(filterMedianResponse())
    }

    private fun performNtpAlgorithm(): Function<Flux<InetAddress>, Mono<NtpTiming>> {
        return Function { inetAddress ->

            inetAddress
                    .flatMap(bestResponseAgainstSingleIp(5))
                    .take(5)
                    .collectList()
                    .filter { ntpTimings -> !ntpTimings.isEmpty() }
                    .map(filterMedianResponse())

            Mono.just(NtpTiming(1L, 1L))
            //NtpTiming(1L, 1L)
        }
    }

    private fun bestResponseAgainstSingleIp(repeatCount: Int): Function<InetAddress, Flux<NtpTiming>> {
        return Function { singleIp ->
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

                        // gather ntp timing !!!
                        //Flux.just(NtpTiming(1L, 1L))
                    }
                    .collectList()
                    .map(filterLeastRoundTripDelay())
                    .flux()
        }
    }

    private fun filterLeastRoundTripDelay(): Function<List<NtpTiming>, NtpTiming> {
        return Function { ntpTimings ->
            val ntpTimingWithBestRoundTrip = ntpTimings
                    .sortedBy(NtpTiming::delay)
                    .first()

            ntpTimingWithBestRoundTrip
        }
    }

    private fun filterMedianResponse(): Function<List<NtpTiming>, NtpTiming> {
        return Function { ntpTimings ->
            val sortedByDuration = ntpTimings
                    .sortedBy(NtpTiming::delay)

            sortedByDuration[sortedByDuration.size / 2]
        }
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