package com.pulseboard.api

import com.pulseboard.core.StatsService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import kotlin.test.assertEquals

class StatsControllerTest {
    private lateinit var mockStatsService: StatsService
    private lateinit var statsController: StatsController
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setup() {
        mockStatsService = mockk()
        statsController = StatsController(mockStatsService)
        webTestClient = WebTestClient.bindToController(statsController).build()
    }

    @Test
    fun `should return stats overview with correct structure`() =
        runTest {
            val mockStats =
                StatsService.StatsOverview(
                    eventsPerMin = 42,
                    alertsPerMin = 5,
                    uptimeSec = 3600L,
                )
            coEvery { mockStatsService.getStats() } returns mockStats

            val result = statsController.getOverview()

            assertEquals(42, result["eventsPerMin"])
            assertEquals(5, result["alertsPerMin"])
            assertEquals(3600L, result["uptimeSec"])
        }

    @Test
    fun `should return JSON response via HTTP`() {
        val mockStats =
            StatsService.StatsOverview(
                eventsPerMin = 10,
                alertsPerMin = 2,
                uptimeSec = 1800L,
            )
        coEvery { mockStatsService.getStats() } returns mockStats

        webTestClient.get()
            .uri("/stats/overview")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.eventsPerMin").isEqualTo(10)
            .jsonPath("$.alertsPerMin").isEqualTo(2)
            .jsonPath("$.uptimeSec").isEqualTo(1800)
    }

    @Test
    fun `should handle zero stats correctly`() =
        runTest {
            val mockStats =
                StatsService.StatsOverview(
                    eventsPerMin = 0,
                    alertsPerMin = 0,
                    uptimeSec = 0L,
                )
            coEvery { mockStatsService.getStats() } returns mockStats

            val result = statsController.getOverview()

            assertEquals(0, result["eventsPerMin"])
            assertEquals(0, result["alertsPerMin"])
            assertEquals(0L, result["uptimeSec"])
        }

    @Test
    fun `should handle large numbers correctly`() =
        runTest {
            val mockStats =
                StatsService.StatsOverview(
                    eventsPerMin = 999999,
                    alertsPerMin = 50000,
                    // 24 hours
                    uptimeSec = 86400L,
                )
            coEvery { mockStatsService.getStats() } returns mockStats

            val result = statsController.getOverview()

            assertEquals(999999, result["eventsPerMin"])
            assertEquals(50000, result["alertsPerMin"])
            assertEquals(86400L, result["uptimeSec"])
        }
}
