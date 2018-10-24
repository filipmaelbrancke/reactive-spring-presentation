package net.maelbrancke.filip.reactiventp

import net.maelbrancke.filip.reactiventp.ntp.NtpTimer
import net.maelbrancke.filip.reactiventp.ntp.Time
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class TestController(private val ntpTimer: NtpTimer) {

    @GetMapping("/api/test")
    fun test(): Mono<Time> {
        return ntpTimer.initialize()
    }
}