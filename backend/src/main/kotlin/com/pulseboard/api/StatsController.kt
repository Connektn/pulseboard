package com.pulseboard.api

import com.pulseboard.core.StatsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatsController(
    @Autowired private val statsService: StatsService,
) {
    @GetMapping("/stats/overview")
    suspend fun getOverview(): Map<String, Any> {
        val stats = statsService.getStats()
        return mapOf(
            "eventsPerMin" to stats.eventsPerMin,
            "alertsPerMin" to stats.alertsPerMin,
            "uptimeSec" to stats.uptimeSec
        )
    }
}