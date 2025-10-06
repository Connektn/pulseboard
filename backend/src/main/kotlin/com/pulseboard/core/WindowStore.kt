package com.pulseboard.core

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.TimeUnit

@Component
class WindowStore(
    // Configuration
    @Value("\${window-store.default-size:5m}") private val defaultWindowSize: Duration,
    @Value("\${window-store.max-cache-size:10000}") maxCacheSize: Long,
    @Value("\${window-store.cache-expiration:10m}") expireAfter: Duration,
    private val clock: Clock,
) {

    // Caches for different data structures
    private val timeSeriesCache: Cache<WindowKey, TimeSeriesWindow> =
        Caffeine.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfterAccess(expireAfter.toSeconds(), TimeUnit.SECONDS)
            .build()

    private val ewmaCache: Cache<EwmaKey, EwmaState> =
        Caffeine.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfterAccess(expireAfter.toSeconds(), TimeUnit.SECONDS)
            .build()

    /**
     * Append a timestamped value to the time series window
     */
    fun append(
        entityId: String,
        type: String,
        timestamp: Instant,
        value: Long,
    ) {
        val key = WindowKey(entityId, type)
        val window = timeSeriesCache.get(key) { TimeSeriesWindow() }
        window.append(timestamp, value)

        // Prune old data beyond the horizon
        val horizon = timestamp.minus(defaultWindowSize)
        window.pruneOldData(horizon)
    }

    /**
     * Calculate rate per minute for a specific key
     */
    fun ratePerMin(
        entityId: String,
        type: String,
    ): Double {
        val key = WindowKey(entityId, type)
        val window = timeSeriesCache.getIfPresent(key) ?: return 0.0

        val now = clock.instant()
        val oneMinuteAgo = now.minus(Duration.ofMinutes(1))

        val count = window.countInRange(oneMinuteAgo, now)
        return count.toDouble() // Already per minute since we're counting over 1 minute
    }

    /**
     * Sum values within a specific duration
     */
    fun sumIn(
        entityId: String,
        type: String,
        duration: Duration,
    ): Long {
        val key = WindowKey(entityId, type)
        val window = timeSeriesCache.getIfPresent(key) ?: return 0L

        val now = clock.instant()
        val start = now.minus(duration)

        return window.sumInRange(start, now)
    }

    /**
     * Count entries within a specific duration
     */
    fun countIn(
        entityId: String,
        type: String,
        duration: Duration,
    ): Long {
        val key = WindowKey(entityId, type)
        val window = timeSeriesCache.getIfPresent(key) ?: return 0L

        val now = clock.instant()
        val start = now.minus(duration)

        return window.countInRange(start, now)
    }

    /**
     * Update and get EWMA (Exponentially Weighted Moving Average)
     */
    fun updateEwma(
        entityId: String,
        type: String,
        value: Double,
        alpha: Double = 0.1,
    ): Double {
        val key = EwmaKey(entityId, type)
        val state = ewmaCache.get(key) { EwmaState(value, clock.instant()) }

        // Update EWMA: ewma = alpha * current_value + (1 - alpha) * previous_ewma
        val newEwma = alpha * value + (1 - alpha) * state.value
        val newState = EwmaState(newEwma, clock.instant())

        ewmaCache.put(key, newState)
        return newEwma
    }

    /**
     * Get current EWMA value
     */
    fun getEwma(
        entityId: String,
        type: String,
    ): Double {
        val key = EwmaKey(entityId, type)
        return ewmaCache.getIfPresent(key)?.value ?: 0.0
    }

    /**
     * Get average over the last N minutes
     */
    fun avgOverLast(
        entityId: String,
        type: String,
        minutes: Long,
    ): Double {
        val duration = Duration.ofMinutes(minutes)
        val sum = sumIn(entityId, type, duration)
        val count = countIn(entityId, type, duration)

        return if (count > 0) sum.toDouble() / count.toDouble() else 0.0
    }

    /**
     * Clear all caches (useful for testing)
     */
    fun clear() {
        timeSeriesCache.invalidateAll()
        ewmaCache.invalidateAll()
    }

    // Data classes for cache keys
    data class WindowKey(val entityId: String, val type: String)

    data class EwmaKey(val entityId: String, val type: String)

    data class EwmaState(val value: Double, val lastUpdate: Instant)

    // Time series data point
    data class DataPoint(val timestamp: Instant, val value: Long)

    // Ring buffer-like time series window
    class TimeSeriesWindow {
        private val data = ConcurrentLinkedDeque<DataPoint>()

        fun append(
            timestamp: Instant,
            value: Long,
        ) {
            data.addLast(DataPoint(timestamp, value))
        }

        fun pruneOldData(horizon: Instant) {
            while (data.isNotEmpty() && data.peekFirst().timestamp.isBefore(horizon)) {
                data.removeFirst()
            }
        }

        fun countInRange(
            start: Instant,
            end: Instant,
        ): Long {
            return data.count { point ->
                !point.timestamp.isBefore(start) && !point.timestamp.isAfter(end)
            }.toLong()
        }

        fun sumInRange(
            start: Instant,
            end: Instant,
        ): Long {
            return data.filter { point ->
                !point.timestamp.isBefore(start) && !point.timestamp.isAfter(end)
            }.sumOf { it.value }
        }

        fun size(): Int = data.size
    }
}
