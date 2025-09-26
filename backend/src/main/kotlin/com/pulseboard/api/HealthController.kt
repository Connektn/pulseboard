package com.pulseboard.api

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/health")
    suspend fun health(): Map<String, String> {
        return mapOf("status" to "UP")
    }
}
