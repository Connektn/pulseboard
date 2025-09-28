package com.pulseboard.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

@Service
class StatsService {
    private val startupTime = Instant.now()
    private val eventCount = AtomicLong(0)
    private val alertCount = AtomicLong(0)

    private val mutex = Mutex()
    private val eventTimestamps = mutableListOf<Instant>()
    private val alertTimestamps = mutableListOf<Instant>()

    fun recordEvent() {
        eventCount.incrementAndGet()
        recordTimestamp(eventTimestamps)
    }

    fun recordAlert() {
        alertCount.incrementAndGet()
        recordTimestamp(alertTimestamps)
    }

    private fun recordTimestamp(timestamps: MutableList<Instant>) {
        val now = Instant.now()
        // Use a coroutine context to handle the mutex, but since this might be called from
        // non-suspending context, we'll use a simple synchronized approach instead
        synchronized(timestamps) {
            timestamps.add(now)
            // Keep only timestamps from the last minute
            val oneMinuteAgo = now.minusSeconds(60)
            timestamps.removeAll { it.isBefore(oneMinuteAgo) }
        }
    }

    suspend fun getStats(): StatsOverview {
        return mutex.withLock {
            val now = Instant.now()
            val oneMinuteAgo = now.minusSeconds(60)

            // Clean up old timestamps
            eventTimestamps.removeAll { it.isBefore(oneMinuteAgo) }
            alertTimestamps.removeAll { it.isBefore(oneMinuteAgo) }

            StatsOverview(
                eventsPerMin = eventTimestamps.size,
                alertsPerMin = alertTimestamps.size,
                uptimeSec = java.time.Duration.between(startupTime, now).seconds,
            )
        }
    }

    data class StatsOverview(
        val eventsPerMin: Int,
        val alertsPerMin: Int,
        val uptimeSec: Long,
    )
}
