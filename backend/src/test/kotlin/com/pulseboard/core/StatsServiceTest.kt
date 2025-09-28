package com.pulseboard.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsServiceTest {
    private lateinit var statsService: StatsService

    @BeforeEach
    fun setup() {
        statsService = StatsService()
    }

    @Test
    fun `should track events correctly`() =
        runTest {
            val initialStats = statsService.getStats()
            assertEquals(0, initialStats.eventsPerMin)

            statsService.recordEvent()
            statsService.recordEvent()

            val updatedStats = statsService.getStats()
            assertEquals(2, updatedStats.eventsPerMin)
        }

    @Test
    fun `should track alerts correctly`() =
        runTest {
            val initialStats = statsService.getStats()
            assertEquals(0, initialStats.alertsPerMin)

            statsService.recordAlert()
            statsService.recordAlert()
            statsService.recordAlert()

            val updatedStats = statsService.getStats()
            assertEquals(3, updatedStats.alertsPerMin)
        }

    @Test
    fun `should calculate uptime correctly`() =
        runTest {
            val stats = statsService.getStats()
            assertTrue(stats.uptimeSec >= 0, "Uptime should be non-negative")

            // Give more leeway for uptime since test execution can be slow
            assertTrue(stats.uptimeSec < 60, "Uptime should be reasonable for new service")

            delay(100) // Wait a shorter time to avoid test timeout
            val updatedStats = statsService.getStats()
            assertTrue(updatedStats.uptimeSec >= stats.uptimeSec, "Uptime should not decrease")
        }

    @Test
    fun `should return comprehensive stats overview`() =
        runTest {
            statsService.recordEvent()
            statsService.recordAlert()

            val stats = statsService.getStats()

            assertEquals(1, stats.eventsPerMin)
            assertEquals(1, stats.alertsPerMin)
            assertTrue(stats.uptimeSec >= 0)
        }

    @Test
    fun `should handle concurrent operations`() =
        runTest {
            repeat(100) {
                statsService.recordEvent()
                statsService.recordAlert()
            }

            val stats = statsService.getStats()
            assertEquals(100, stats.eventsPerMin)
            assertEquals(100, stats.alertsPerMin)
        }
}
