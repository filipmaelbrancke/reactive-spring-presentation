package net.maelbrancke.filip.reactiventp.ntp

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.scheduler.Schedulers
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

    private val initialized = false

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
        return Mono
                .just(ntpPoolAddress)
                .compose(resolveNtpPoolToIpAddresses())
                .doOnNext { ipAddress -> System.out.println(ipAddress) }
                .map { inetAddress -> NtpTiming(1L, 1L) }
    }

    private fun resolveNtpPoolToIpAddresses(): Function<Mono<String>, Flux<InetAddress>> {
        return Function { ntpPool ->
            ntpPool
                    .publishOn(Schedulers.parallel())
                    .flatMapMany { ntpPoolAddress ->
                        try {
                            InetAddress.getAllByName(ntpPoolAddress).toFlux()
                        } catch (e: UnknownHostException) {
                            e.toFlux<InetAddress>()
                        }
                    }
        }
    }

    /*private fun performNtpAlgorithm(): Function<Flux<InetAddress>, Mono<NtpTiming>> {
        return Function { InetAddress ->

        }
    }

    private fun bestResponseAgainstSingleIp(repeatCount: Int)*/

    private fun calculateNow(): Time {
        val currentTime = System.currentTimeMillis()
        val offset = timingInfo?.localClockOffset ?: 0
        val ntpTime = System.currentTimeMillis() + offset
        val deviation = timingInfo?.delay ?: 0
        return Time(timeInMillis = currentTime, ntpTimeInMillis = ntpTime, deviation = deviation)
    }
}