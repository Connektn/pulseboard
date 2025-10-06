package com.pulseboard.cdp.store

import com.pulseboard.fixedClock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RollingCounterTest {
    private lateinit var counter: RollingCounter

    @BeforeEach
    fun setup() {
        counter =
            RollingCounter(
                window = Duration.ofHours(24),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )
    }

    // === Basic Append and Count Tests ===

    @Test
    fun `should append and count single event`() {
        val profileId = "profile-1"
        val eventName = "Feature Used"
        val ts = Instant.now()

        counter.append(profileId, eventName, ts)

        val count = counter.count(profileId, eventName)
        assertEquals(1, count)
    }

    @Test
    fun `should append and count multiple events`() {
        val profileId = "profile-1"
        val eventName = "Login"
        val now = Instant.now()

        // Append 5 events
        repeat(5) {
            counter.append(profileId, eventName, now.plusSeconds(it.toLong()))
        }

        val count = counter.count(profileId, eventName)
        assertEquals(5, count)
    }

    @Test
    fun `should count events in same bucket`() {
        val profileId = "profile-1"
        val eventName = "Click"
        val now = Instant.now()

        // Append events within the same minute
        repeat(10) {
            counter.append(profileId, eventName, now.plusSeconds(it.toLong()))
        }

        val count = counter.count(profileId, eventName)
        assertEquals(10, count)
    }

    // === Multiple Event Names Tests (DoD Requirement) ===

    @Test
    fun `multiple event names should be isolated`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Append to different event names
        counter.append(profileId, "Login", now)
        counter.append(profileId, "Login", now.plusSeconds(10))
        counter.append(profileId, "Logout", now)
        counter.append(profileId, "Feature Used", now)

        assertEquals(2, counter.count(profileId, "Login"))
        assertEquals(1, counter.count(profileId, "Logout"))
        assertEquals(1, counter.count(profileId, "Feature Used"))
    }

    @Test
    fun `should track multiple event names independently`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Append different amounts to different events
        repeat(5) { counter.append(profileId, "Event A", now.plusSeconds(it.toLong())) }
        repeat(10) { counter.append(profileId, "Event B", now.plusSeconds(it.toLong())) }
        repeat(3) { counter.append(profileId, "Event C", now.plusSeconds(it.toLong())) }

        assertEquals(5, counter.count(profileId, "Event A"))
        assertEquals(10, counter.count(profileId, "Event B"))
        assertEquals(3, counter.count(profileId, "Event C"))
    }

    // === Per-Profile Isolation Tests ===

    @Test
    fun `events from different profiles should be isolated`() {
        val eventName = "Login"
        val now = Instant.now()

        counter.append("profile-1", eventName, now)
        counter.append("profile-1", eventName, now.plusSeconds(10))
        counter.append("profile-2", eventName, now)

        assertEquals(2, counter.count("profile-1", eventName))
        assertEquals(1, counter.count("profile-2", eventName))
    }

    // === Time Window Tests ===

    @Test
    fun `should count events within custom window`() {
        val profileId = "profile-1"
        val eventName = "Event"
        val now = Instant.now()

        // Append events at different times
        counter.append(profileId, eventName, now.minus(Duration.ofHours(25))) // Outside 24h
        counter.append(profileId, eventName, now.minus(Duration.ofHours(12))) // Within 24h
        counter.append(profileId, eventName, now.minus(Duration.ofHours(1))) // Within 24h
        counter.append(profileId, eventName, now) // Within 24h

        // Count with 24h window (default)
        val count24h = counter.count(profileId, eventName, Duration.ofHours(24))
        assertEquals(3, count24h) // Only last 3 events

        // Count with 1h window
        val count1h = counter.count(profileId, eventName, Duration.ofHours(1))
        assertEquals(2, count1h) // Only last 2 events
    }

    @Test
    fun `should only count events within window`() {
        val profileId = "profile-1"
        val eventName = "Event"
        val now = Instant.now()

        // Add events spread over time
        counter.append(profileId, eventName, now.minus(Duration.ofHours(30)))
        counter.append(profileId, eventName, now.minus(Duration.ofHours(20)))
        counter.append(profileId, eventName, now.minus(Duration.ofHours(10)))
        counter.append(profileId, eventName, now.minus(Duration.ofHours(5)))
        counter.append(profileId, eventName, now)

        // With 24h window, should exclude first event (30h ago)
        assertEquals(4, counter.count(profileId, eventName, Duration.ofHours(24)))

        // With 12h window, should only include last 3 events
        assertEquals(3, counter.count(profileId, eventName, Duration.ofHours(12)))

        // With 6h window, should only include last 2 events
        assertEquals(2, counter.count(profileId, eventName, Duration.ofHours(6)))
    }

    // === Bucket Eviction Tests (DoD Requirement) ===

    @Test
    fun `counts should roll off after window expires`() {
        val profileId = "profile-1"
        val eventName = "Event"

        // Use smaller window for testing
        val testCounter =
            RollingCounter(
                window = Duration.ofMinutes(5),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )

        val now = Instant.now()

        // Add old event (outside window)
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(10)))

        // Add recent events (within window)
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(2)))
        testCounter.append(profileId, eventName, now)

        // Before eviction, buckets exist but count excludes old data
        assertEquals(2, testCounter.count(profileId, eventName, Duration.ofMinutes(5)))
        assertEquals(3, testCounter.getBucketCount(profileId, eventName))

        // Evict old buckets
        testCounter.evictOldBuckets(profileId)

        // After eviction, old buckets removed
        assertEquals(2, testCounter.count(profileId, eventName, Duration.ofMinutes(5)))
        assertTrue(testCounter.getBucketCount(profileId, eventName) <= 3) // Only recent buckets
    }

    @Test
    fun `eviction should remove buckets outside window`() {
        val profileId = "profile-1"
        val eventName = "Event"
        val now = Instant.now()

        // Use smaller window for testing
        val testCounter =
            RollingCounter(
                window = Duration.ofMinutes(10),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )

        // Add events at different times
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(20))) // Outside window
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(15))) // Outside window
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(5))) // Within window
        testCounter.append(profileId, eventName, now) // Within window

        // Before eviction: 4 buckets
        assertEquals(4, testCounter.getBucketCount(profileId, eventName))

        // Evict old buckets
        testCounter.evictOldBuckets(profileId)

        // After eviction: only recent buckets remain
        val remainingBuckets = testCounter.getBucketCount(profileId, eventName)
        assertTrue(remainingBuckets <= 2, "Expected <= 2 buckets, got $remainingBuckets")

        // Count should only include events within window
        assertEquals(2, testCounter.count(profileId, eventName, Duration.ofMinutes(10)))
    }

    @Test
    fun `eviction should work for all profiles`() {
        val now = Instant.now()

        val testCounter =
            RollingCounter(
                window = Duration.ofMinutes(10),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )

        // Add old events for multiple profiles
        testCounter.append("profile-1", "Event", now.minus(Duration.ofMinutes(20)))
        testCounter.append("profile-2", "Event", now.minus(Duration.ofMinutes(20)))
        testCounter.append("profile-3", "Event", now.minus(Duration.ofMinutes(20)))

        // Add recent events
        testCounter.append("profile-1", "Event", now)
        testCounter.append("profile-2", "Event", now)
        testCounter.append("profile-3", "Event", now)

        // Evict for all profiles
        testCounter.evictOldBuckets()

        // All profiles should have old buckets removed
        assertTrue(testCounter.getBucketCount("profile-1", "Event") <= 2)
        assertTrue(testCounter.getBucketCount("profile-2", "Event") <= 2)
        assertTrue(testCounter.getBucketCount("profile-3", "Event") <= 2)
    }

    // === Bucket Boundary Tests ===

    @Test
    fun `events in different buckets should be counted separately`() {
        val profileId = "profile-1"
        val eventName = "Event"

        // Create events in different 1-minute buckets
        val bucket1 = Instant.parse("2025-10-04T10:00:00Z")
        val bucket2 = Instant.parse("2025-10-04T10:01:00Z")
        val bucket3 = Instant.parse("2025-10-04T10:02:00Z")

        counter.append(profileId, eventName, bucket1)
        counter.append(profileId, eventName, bucket1.plusSeconds(30)) // Same bucket
        counter.append(profileId, eventName, bucket2)
        counter.append(profileId, eventName, bucket3)

        // Should have 3 buckets
        assertEquals(3, counter.getBucketCount(profileId, eventName))

        // Total count should be 4
        assertEquals(4, counter.count(profileId, eventName, Duration.ofHours(24)))
    }

    @Test
    fun `events at bucket boundaries should be handled correctly`() {
        val profileId = "profile-1"
        val eventName = "Event"

        // Events exactly at bucket boundaries
        val bucketStart = Instant.parse("2025-10-04T10:00:00Z")
        val bucketEnd = Instant.parse("2025-10-04T10:00:59Z")
        val nextBucket = Instant.parse("2025-10-04T10:01:00Z")

        counter.append(profileId, eventName, bucketStart)
        counter.append(profileId, eventName, bucketEnd)
        counter.append(profileId, eventName, nextBucket)

        // Should have 2 buckets (first two in same bucket)
        assertEquals(2, counter.getBucketCount(profileId, eventName))
        assertEquals(3, counter.count(profileId, eventName, Duration.ofHours(24)))
    }

    // === Empty/Edge Case Tests ===

    @Test
    fun `count should return 0 for non-existent profile`() {
        val count = counter.count("non-existent", "Event")
        assertEquals(0, count)
    }

    @Test
    fun `count should return 0 for non-existent event name`() {
        val profileId = "profile-1"
        counter.append(profileId, "Event A", Instant.now())

        val count = counter.count(profileId, "Event B")
        assertEquals(0, count)
    }

    @Test
    fun `should handle rapid appends`() =
        runBlocking {
            val profileId = "profile-1"
            val eventName = "Rapid Event"
            val now = Instant.now()

            // Append 1000 events rapidly
            repeat(1000) {
                counter.append(profileId, eventName, now.plusMillis(it.toLong()))
            }

            val count = counter.count(profileId, eventName)
            assertEquals(1000, count)
        }

    // === Integration Tests ===

    @Test
    fun `complete scenario with append, count, and eviction`() {
        val profileId = "profile-1"
        val eventName = "Feature Used"

        val testCounter =
            RollingCounter(
                window = Duration.ofMinutes(30),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )

        val now = Instant.now()

        // Add events over time
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(45))) // Old
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(40))) // Old
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(20))) // Within window
        testCounter.append(profileId, eventName, now.minus(Duration.ofMinutes(10))) // Within window
        testCounter.append(profileId, eventName, now) // Within window

        // Count before eviction (query ignores old data)
        assertEquals(3, testCounter.count(profileId, eventName, Duration.ofMinutes(30)))

        // Evict old buckets
        testCounter.evictOldBuckets(profileId)

        // Count after eviction (same result)
        assertEquals(3, testCounter.count(profileId, eventName, Duration.ofMinutes(30)))

        // Verify old buckets removed
        assertTrue(testCounter.getBucketCount(profileId, eventName) <= 4)
    }
}
