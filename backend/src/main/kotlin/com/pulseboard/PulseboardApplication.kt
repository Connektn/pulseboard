package com.pulseboard

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PulseboardApplication

fun main(args: Array<String>) {
    runApplication<PulseboardApplication>(*args)
}
