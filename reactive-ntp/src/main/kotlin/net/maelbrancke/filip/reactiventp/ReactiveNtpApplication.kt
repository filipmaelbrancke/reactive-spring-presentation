package net.maelbrancke.filip.reactiventp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReactiveNtpApplication

fun main(args: Array<String>) {
    runApplication<ReactiveNtpApplication>(*args)
}
