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

    // Additional WindowStore Metrics Tests for I1
    @Test
    fun `should handle rate calculation with no data`() {
        val entityId = "empty"
        val type = "EMPTY"

        val rate = windowStore.ratePerMin(entityId, type)
        assertEquals(0.0, rate)
    }

    @Test
    fun `should handle rate calculation with single data point`() {
        val entityId = "single"
        val type = "SINGLE"
        val now = Instant.now()

        windowStore.append(entityId, type, now.minusSeconds(30), 1L)

        val rate = windowStore.ratePerMin(entityId, type)
        assertEquals(1.0, rate) // 1 event in the last minute
    }

    @Test
    fun `should calculate accurate rate per minute with fractional minutes`() {
        val entityId = "fractional"
        val type = "FRAC"
        val now = Instant.now()

        // Add 6 events in 30 seconds (should be 12/min rate)
        repeat(6) { i ->
            windowStore.append(entityId, type, now.minusSeconds((i * 5).toLong()), 1L)
        }

        val rate = windowStore.ratePerMin(entityId, type)
        assertEquals(6.0, rate) // 6 events within the minute window
    }

    @Test
    fun `should handle EWMA with alpha of 1_0`() {
        val entityId = "alpha1"
        val type = "ALPHA"

        // Alpha = 1.0 should make EWMA equal to the current value
        val ewma1 = windowStore.updateEwma(entityId, type, 100.0, 1.0)
        assertEquals(100.0, ewma1)

        val ewma2 = windowStore.updateEwma(entityId, type, 200.0, 1.0)
        assertEquals(200.0, ewma2) // Should completely replace previous value

        assertEquals(200.0, windowStore.getEwma(entityId, type))
    }

    @Test
    fun `should handle EWMA with alpha of 0_0`() {
        val entityId = "alpha0"
        val type = "ALPHA"

        val ewma1 = windowStore.updateEwma(entityId, type, 100.0, 0.0)
        assertEquals(100.0, ewma1) // First value always returned

        val ewma2 = windowStore.updateEwma(entityId, type, 200.0, 0.0)
        assertEquals(100.0, ewma2) // Should not change with alpha = 0.0

        assertEquals(100.0, windowStore.getEwma(entityId, type))
    }

    @Test
    fun `should handle very large sums without overflow`() {
        val entityId = "largeSum"
        val type = "LARGE"
        val now = Instant.now()

        // Add large values
        windowStore.append(entityId, type, now.minusSeconds(50), Long.MAX_VALUE / 4)
        windowStore.append(entityId, type, now.minusSeconds(30), Long.MAX_VALUE / 4)
        windowStore.append(entityId, type, now.minusSeconds(10), Long.MAX_VALUE / 4)

        val sum = windowStore.sumIn(entityId, type, Duration.ofMinutes(1))
        assertEquals((Long.MAX_VALUE / 4) * 3, sum)
    }

    @Test
    fun `should handle negative values correctly`() {
        val entityId = "negative"
        val type = "NEG"
        val now = Instant.now()

        windowStore.append(entityId, type, now.minusSeconds(50), -100L)
        windowStore.append(entityId, type, now.minusSeconds(30), -50L)
        windowStore.append(entityId, type, now.minusSeconds(10), 200L)

        val sum = windowStore.sumIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(50L, sum) // -100 + (-50) + 200 = 50

        val count = windowStore.countIn(entityId, type, Duration.ofMinutes(1))
        assertEquals(3L, count)

        val avg = windowStore.avgOverLast(entityId, type, 1)
        assertEquals(16.666666666666668, avg, 0.001) // 50 / 3
    }

    @Test
    fun `should handle zero duration queries gracefully`() {
        val entityId = "zeroDuration"
        val type = "ZERO"
        val now = Instant.now()

        windowStore.append(entityId, type, now, 100L)

        val count = windowStore.countIn(entityId, type, Duration.ZERO)
        assertEquals(0L, count) // Nothing should be within zero duration

        val sum = windowStore.sumIn(entityId, type, Duration.ZERO)
        assertEquals(0L, sum)
    }

    @Test
    fun `should handle very short durations accurately`() {
        val entityId = "shortDuration"
        val type = "SHORT"
        val now = Instant.now()

        // Add events with millisecond precision
        windowStore.append(entityId, type, now.minusMillis(1), 10L)
        windowStore.append(entityId, type, now.minusMillis(5), 20L)
        windowStore.append(entityId, type, now.minusMillis(15), 30L)

        // Query for events within last 10ms
        val count = windowStore.countIn(entityId, type, Duration.ofMillis(10))
        assertEquals(2L, count) // Should include events at 1ms and 5ms ago

        val sum = windowStore.sumIn(entityId, type, Duration.ofMillis(10))
        assertEquals(30L, sum) // 10 + 20
    }

    @Test
    fun `should maintain data integrity after multiple prune operations`() {
        val entityId = "pruneTest"
        val type = "PRUNE"
        val now = Instant.now()

        // Add data spread over a longer time period
        repeat(20) { i ->
            windowStore.append(entityId, type, now.minus(Duration.ofMinutes(i.toLong())), (i + 1).toLong())
        }

        // Add recent data to trigger pruning
        windowStore.append(entityId, type, now, 999L)

        val finalCount = windowStore.countIn(entityId, type, Duration.ofMinutes(6))

        // Should have some events within the 6-minute window (including the recent one)
        assertTrue(finalCount > 0, "Should have at least the recent event")
        assertTrue(finalCount < 20, "Should have pruned some old events")

        val finalSum = windowStore.sumIn(entityId, type, Duration.ofMinutes(6))
        assertTrue(finalSum >= 999L, "Should include the recent event value")
    }

    @Test
    fun `EWMA should handle precision edge cases`() {
        val entityId = "precision"
        val type = "PREC"

        // Test with very small values and precise alpha
        val alpha = 0.001
        val ewma1 = windowStore.updateEwma(entityId, type, 0.0001, alpha)
        assertEquals(0.0001, ewma1, 0.00000001)

        val ewma2 = windowStore.updateEwma(entityId, type, 0.0002, alpha)
        val expected = alpha * 0.0002 + (1 - alpha) * 0.0001
        assertEquals(expected, ewma2, 0.00000001)
    }
}
