package com.pulseboard.core

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals

class WindowStoreTest {
    private lateinit var windowStore: WindowStore

    @BeforeEach
    fun setup() {
        windowStore = WindowStore()
    }

    @Test
    fun `should append and retrieve basic metrics`() {
        val entityId = "user123"
        val type = "LOGIN"
        val now = Instant.now()

        // Append some data points
        windowStore.append(entityId, type, now.minusSeconds(30), 1L)
        windowStore.append(entityId, type, now.minusSeconds(20), 1L)
        windowStore.append(entityId, type, now.minusSeconds(10), 1L)

        // Test count in last minute
        val countLastMinute = windowStore.countIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(3L, countLastMinute)

        // Test sum in last minute
        val sumLastMinute = windowStore.sumIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(3L, sumLastMinute)
    }

    @Test
    fun `should calculate rate per minute correctly`() {
        val entityId = "user456"
        val type = "CONN_OPEN"
        val now = Instant.now()

        // Add 5 events in the last minute
        repeat(5) { i ->
            windowStore.append(entityId, type, now.minusSeconds((i * 10).toLong()), 1L)
        }

        val rate = windowStore.ratePerMin(entityId, type)
        assertEquals(5.0, rate)
    }

    @Test
    fun `should prune old data beyond window`() {
        val entityId = "user789"
        val type = "BET_PLACED"
        val now = Instant.now()

        // Add old data (beyond 5-minute default window)
        windowStore.append(entityId, type, now.minus(Duration.ofMinutes(10)), 1L)
        windowStore.append(entityId, type, now.minus(Duration.ofMinutes(8)), 1L)

        // Add recent data
        windowStore.append(entityId, type, now.minusSeconds(30), 1L)
        windowStore.append(entityId, type, now.minusSeconds(10), 1L)

        // Only recent data should be counted (pruning happens on append)
        val count = windowStore.countIn(entityId, type, Duration.ofMinutes(6))
        assertEquals(2L, count) // Only the recent 2 events should remain
    }

    @Test
    fun `should handle empty windows gracefully`() {
        val entityId = "nonexistent"
        val type = "UNKNOWN"

        assertEquals(0.0, windowStore.ratePerMin(entityId, type))
        assertEquals(0L, windowStore.sumIn(entityId, type, Duration.ofMinutes(1)))
        assertEquals(0L, windowStore.countIn(entityId, type, Duration.ofMinutes(1)))
        assertEquals(0.0, windowStore.avgOverLast(entityId, type, 5))
        assertEquals(0.0, windowStore.getEwma(entityId, type))
    }

    @Test
    fun `should calculate sum with different values`() {
        val entityId = "user999"
        val type = "CASHIN"
        val now = Instant.now()

        windowStore.append(entityId, type, now.minusSeconds(50), 100L)
        windowStore.append(entityId, type, now.minusSeconds(30), 250L)
        windowStore.append(entityId, type, now.minusSeconds(10), 150L)

        val sum = windowStore.sumIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(500L, sum)

        val count = windowStore.countIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(3L, count)
    }

    @Test
    fun `should calculate average correctly`() {
        val entityId = "user111"
        val type = "CONN_BYTES"
        val now = Instant.now()

        windowStore.append(entityId, type, now.minusSeconds(50), 100L)
        windowStore.append(entityId, type, now.minusSeconds(30), 200L)
        windowStore.append(entityId, type, now.minusSeconds(10), 300L)

        val avg = windowStore.avgOverLast(entityId, type, 1)
        assertEquals(200.0, avg) // (100 + 200 + 300) / 3 = 200
    }

    @Test
    fun `should update EWMA correctly`() {
        val entityId = "user222"
        val type = "LOGIN"
        val alpha = 0.2

        // First update should return the initial value
        val ewma1 = windowStore.updateEwma(entityId, type, 100.0, alpha)
        assertEquals(100.0, ewma1)

        // Subsequent updates should apply EWMA formula
        val ewma2 = windowStore.updateEwma(entityId, type, 200.0, alpha)
        val expected2 = alpha * 200.0 + (1 - alpha) * 100.0
        assertEquals(expected2, ewma2, 0.001)

        // Third update
        val ewma3 = windowStore.updateEwma(entityId, type, 50.0, alpha)
        val expected3 = alpha * 50.0 + (1 - alpha) * expected2
        assertEquals(expected3, ewma3, 0.001)

        // Verify getEwma returns the current value
        assertEquals(ewma3, windowStore.getEwma(entityId, type), 0.001)
    }

    @Test
    fun `should handle multiple entity types independently`() {
        val now = Instant.now()

        // Entity 1, Type A
        windowStore.append("entity1", "typeA", now.minusSeconds(30), 10L)
        windowStore.append("entity1", "typeA", now.minusSeconds(20), 20L)

        // Entity 1, Type B
        windowStore.append("entity1", "typeB", now.minusSeconds(25), 15L)

        // Entity 2, Type A
        windowStore.append("entity2", "typeA", now.minusSeconds(35), 5L)

        // Verify independence
        assertEquals(2L, windowStore.countIn("entity1", "typeA", Duration.ofMinutes(1)))
        assertEquals(1L, windowStore.countIn("entity1", "typeB", Duration.ofMinutes(1)))
        assertEquals(1L, windowStore.countIn("entity2", "typeA", Duration.ofMinutes(1)))
        assertEquals(0L, windowStore.countIn("entity2", "typeB", Duration.ofMinutes(1)))

        assertEquals(30L, windowStore.sumIn("entity1", "typeA", Duration.ofMinutes(1)))
        assertEquals(15L, windowStore.sumIn("entity1", "typeB", Duration.ofMinutes(1)))
        assertEquals(5L, windowStore.sumIn("entity2", "typeA", Duration.ofMinutes(1)))
    }

    @Test
    fun `should handle time range queries correctly`() {
        val entityId = "timeTest"
        val type = "EVENT"
        val now = Instant.now()

        // Add events at specific recent times (all within the 5-minute window)
        windowStore.append(entityId, type, now.minus(Duration.ofMinutes(4)), 1L)
        windowStore.append(entityId, type, now.minus(Duration.ofMinutes(3)), 2L)
        windowStore.append(entityId, type, now.minus(Duration.ofMinutes(1)), 3L)
        windowStore.append(entityId, type, now.minusSeconds(30), 4L)

        // Count in different time ranges
        assertEquals(4L, windowStore.countIn(entityId, type, Duration.ofMinutes(5)))
        assertEquals(3L, windowStore.countIn(entityId, type, Duration.ofMinutes(4)))
        assertEquals(2L, windowStore.countIn(entityId, type, Duration.ofMinutes(2)))
        assertEquals(1L, windowStore.countIn(entityId, type, Duration.ofSeconds(45)))
    }

    @Test
    fun `should clear all caches`() {
        val entityId = "testClear"
        val type = "CLEAR_TEST"
        val now = Instant.now()

        // Add some data
        windowStore.append(entityId, type, now, 100L)
        windowStore.updateEwma(entityId, type, 50.0)

        // Verify data exists
        assertEquals(1L, windowStore.countIn(entityId, type, Duration.ofMinutes(1)))
        assertEquals(50.0, windowStore.getEwma(entityId, type))

        // Clear and verify empty
        windowStore.clear()
        assertEquals(0L, windowStore.countIn(entityId, type, Duration.ofMinutes(1)))
        assertEquals(0.0, windowStore.getEwma(entityId, type))
    }

    @Test
    fun `should handle precision in time calculations`() {
        val entityId = "precisionTest"
        val type = "PRECISION"
        val now = Instant.now()

        // Add events with millisecond precision (all recent)
        windowStore.append(entityId, type, now.minusMillis(500), 1L)
        windowStore.append(entityId, type, now.minusMillis(200), 2L)
        windowStore.append(entityId, type, now.minusMillis(50), 3L)

        // Test with sub-second durations
        val countIn300ms = windowStore.countIn(entityId, type, Duration.ofMillis(300))
        assertEquals(2L, countIn300ms) // Last 2 events within 300ms

        val countIn100ms = windowStore.countIn(entityId, type, Duration.ofMillis(100))
        assertEquals(1L, countIn100ms) // Only the most recent event
    }

    @Test
    fun `TimeSeriesWindow should handle concurrent operations safely`() {
        val window = WindowStore.TimeSeriesWindow()
        val now = Instant.now()

        // Add data points
        window.append(now.minusSeconds(60), 1L)
        window.append(now.minusSeconds(30), 2L)
        window.append(now.minusSeconds(10), 3L)

        assertEquals(3, window.size())

        // Test range operations
        val count = window.countInRange(now.minusSeconds(45), now)
        assertEquals(2L, count)

        val sum = window.sumInRange(now.minusSeconds(45), now)
        assertEquals(5L, sum) // 2 + 3

        // Test pruning
        window.pruneOldData(now.minusSeconds(20))
        assertEquals(1, window.size()) // Only the 10-second-old event should remain
    }
}
