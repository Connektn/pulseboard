package com.pulseboard.cdp.store

import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Rolling counter for tracking event counts over time windows.
 *
 * Features:
 * - 1-minute time buckets
 * - Default 24-hour window
 * - Automatic bucket eviction
 * - Per (profileId, eventName) isolation
 */
class RollingCounter(
    private val window: Duration = Duration.ofHours(24),
    private val bucketSize: Duration = Duration.ofMinutes(1)
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Storage: Map<ProfileId, Map<EventName, TreeMap<BucketTimestamp, Count>>>
    private val counters = ConcurrentHashMap<String, ConcurrentHashMap<String, TreeMap<Long, Long>>>()

    /**
     * Append an event occurrence.
     *
     * @param profileId The profile identifier
     * @param name The event name
     * @param ts The event timestamp
     */
    fun append(profileId: String, name: String, ts: Instant) {
        val bucketTs = toBucketTimestamp(ts)

        // Get or create the profile's counters
        val profileCounters = counters.computeIfAbsent(profileId) {
            ConcurrentHashMap()
        }

        // Get or create the event name's buckets
        val buckets = profileCounters.computeIfAbsent(name) {
            TreeMap()
        }

        // Increment the count for this bucket
        synchronized(buckets) {
            buckets[bucketTs] = buckets.getOrDefault(bucketTs, 0L) + 1
        }

        logger.debug("Appended event: profileId={}, name={}, bucket={}, count={}",
            profileId, name, bucketTs, buckets[bucketTs])
    }

    /**
     * Query the count for a profile and event name within a time window.
     *
     * @param profileId The profile identifier
     * @param name The event name
     * @param window The time window (default: 24 hours)
     * @return The total count within the window
     */
    fun count(profileId: String, name: String, window: Duration = this.window): Long {
        val profileCounters = counters[profileId] ?: return 0L
        val buckets = profileCounters[name] ?: return 0L

        val now = Instant.now()
        val cutoff = now.minus(window)
        val cutoffBucket = toBucketTimestamp(cutoff)

        var total = 0L

        synchronized(buckets) {
            // Sum all buckets within the window
            buckets.tailMap(cutoffBucket, true).values.forEach { count ->
                total += count
            }
        }

        logger.debug("Count query: profileId={}, name={}, window={}, result={}",
            profileId, name, window, total)

        return total
    }

    /**
     * Evict old buckets outside the window.
     * This should be called periodically to prevent memory buildup.
     *
     * @param profileId Optional profile ID to evict for (if null, evicts all)
     */
    fun evictOldBuckets(profileId: String? = null) {
        val now = Instant.now()
        val cutoff = now.minus(window)
        val cutoffBucket = toBucketTimestamp(cutoff)

        if (profileId != null) {
            // Evict for specific profile
            evictForProfile(profileId, cutoffBucket)
        } else {
            // Evict for all profiles
            counters.keys.forEach { pid ->
                evictForProfile(pid, cutoffBucket)
            }
        }
    }

    /**
     * Evict old buckets for a specific profile.
     */
    private fun evictForProfile(profileId: String, cutoffBucket: Long) {
        val profileCounters = counters[profileId] ?: return

        profileCounters.forEach { (name, buckets) ->
            synchronized(buckets) {
                val oldBuckets = buckets.headMap(cutoffBucket, false)
                val evictedCount = oldBuckets.size

                if (evictedCount > 0) {
                    oldBuckets.clear()
                    logger.debug("Evicted {} old buckets for profileId={}, name={}",
                        evictedCount, profileId, name)
                }
            }
        }
    }

    /**
     * Convert an instant to a bucket timestamp (start of bucket in millis).
     */
    private fun toBucketTimestamp(ts: Instant): Long {
        val millis = ts.toEpochMilli()
        val bucketMillis = bucketSize.toMillis()
        return (millis / bucketMillis) * bucketMillis
    }

    /**
     * Get all event names for a profile (for testing/debugging).
     */
    fun getEventNames(profileId: String): Set<String> {
        return counters[profileId]?.keys?.toSet() ?: emptySet()
    }

    /**
     * Get bucket count for a profile and event name (for testing).
     */
    fun getBucketCount(profileId: String, name: String): Int {
        return counters[profileId]?.get(name)?.size ?: 0
    }

    /**
     * Clear all data (for testing).
     */
    fun clear() {
        counters.clear()
    }

    /**
     * Get total number of profiles tracked.
     */
    fun getProfileCount(): Int = counters.size

    /**
     * Get all buckets for a profile and event name (for testing).
     */
    fun getBuckets(profileId: String, name: String): Map<Long, Long> {
        val profileCounters = counters[profileId] ?: return emptyMap()
        val buckets = profileCounters[name] ?: return emptyMap()

        synchronized(buckets) {
            return TreeMap(buckets)
        }
    }
}
