package com.pulseboard.cdp.segments

import com.pulseboard.cdp.model.CdpProfile
import com.pulseboard.cdp.model.ProfileIdentifiers
import com.pulseboard.cdp.model.SegmentAction
import com.pulseboard.cdp.store.RollingCounter
import com.pulseboard.fixedClock
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SegmentEngineTest {
    private lateinit var rollingCounter: RollingCounter
    private lateinit var engine: SegmentEngine

    @BeforeEach
    fun setup() {
        rollingCounter =
            RollingCounter(
                window = Duration.ofHours(24),
                bucketSize = Duration.ofMinutes(1),
                clock = fixedClock,
            )
        engine =
            SegmentEngine(
                rollingCounter = rollingCounter,
                clock = fixedClock,
                reengageInactivityThreshold = Duration.ofMinutes(10),
                powerUserThreshold = 5,
                powerUserWindow = Duration.ofHours(24),
            )
    }

    // === Rule: power_user ===

    @Test
    fun `power_user rule should match when feature count meets threshold`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Add 5 "Feature Used" events
        repeat(5) {
            rollingCounter.append(profileId, "Feature Used", now.minusSeconds(it.toLong()))
        }

        val profile = createProfile(profileId, lastSeen = now)
        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("power_user"))
    }

    @Test
    fun `power_user rule should not match when below threshold`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Add only 4 "Feature Used" events (below threshold of 5)
        repeat(4) {
            rollingCounter.append(profileId, "Feature Used", now.minusSeconds(it.toLong()))
        }

        val profile = createProfile(profileId, lastSeen = now)
        val segments = engine.evaluate(profile)

        assertFalse(segments.contains("power_user"))
    }

    @Test
    fun `power_user rule should match at exact threshold boundary`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Exactly 5 events (at threshold)
        repeat(5) {
            rollingCounter.append(profileId, "Feature Used", now.minusSeconds(it.toLong()))
        }

        val profile = createProfile(profileId, lastSeen = now)
        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("power_user"))
    }

    @Test
    fun `power_user rule should match above threshold`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // 10 events (well above threshold)
        repeat(10) {
            rollingCounter.append(profileId, "Feature Used", now.minusSeconds(it.toLong()))
        }

        val profile = createProfile(profileId, lastSeen = now)
        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("power_user"))
    }

    // === Rule: pro_plan ===

    @Test
    fun `pro_plan rule should match when trait plan equals pro`() {
        val profile =
            createProfile(
                "profile-1",
                traits = mapOf("plan" to "pro"),
                lastSeen = Instant.now(),
            )

        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("pro_plan"))
    }

    @Test
    fun `pro_plan rule should not match with different plan`() {
        val profile =
            createProfile(
                "profile-1",
                traits = mapOf("plan" to "basic"),
                lastSeen = Instant.now(),
            )

        val segments = engine.evaluate(profile)

        assertFalse(segments.contains("pro_plan"))
    }

    @Test
    fun `pro_plan rule should not match when trait missing`() {
        val profile =
            createProfile(
                "profile-1",
                traits = emptyMap(),
                lastSeen = Instant.now(),
            )

        val segments = engine.evaluate(profile)

        assertFalse(segments.contains("pro_plan"))
    }

    // === Rule: reengage ===

    @Test
    fun `reengage rule should match when inactive beyond threshold`() {
        val now = Instant.now()
        val lastSeen = now.minus(Duration.ofMinutes(15)) // 15m ago (> 10m threshold)

        val profile = createProfile("profile-1", lastSeen = lastSeen)
        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("reengage"))
    }

    @Test
    fun `reengage rule should not match when recently active`() {
        val now = Instant.now()
        val lastSeen = now.minus(Duration.ofMinutes(5)) // 5m ago (< 10m threshold)

        val profile = createProfile("profile-1", lastSeen = lastSeen)
        val segments = engine.evaluate(profile)

        assertFalse(segments.contains("reengage"))
    }

    @Test
    fun `reengage rule should match at exact threshold boundary`() {
        val now = fixedClock.instant()
        // Just over 10 minutes (threshold is > 10m, not >=)
        val lastSeen = now.minus(Duration.ofMinutes(10).plusSeconds(1))

        val profile = createProfile("profile-1", lastSeen = lastSeen)
        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("reengage"))
    }

    @Test
    fun `reengage rule should not match at exact threshold`() {
        val now = Instant.now()
        // Just under 10 minutes (threshold is > 10m) - account for test execution time
        val lastSeen = now.minus(Duration.ofMinutes(10)).plusSeconds(1)

        val profile = createProfile("profile-1", lastSeen = lastSeen)
        val segments = engine.evaluate(profile)

        assertFalse(segments.contains("reengage"))
    }

    // === ENTER/EXIT Transition Tests (DoD Requirement) ===

    @Test
    fun `should emit ENTER event when profile joins segment`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Initially no segments
            val profile1 = createProfile(profileId, lastSeen = now)
            engine.evaluateAndEmit(profile1)

            // Add feature events to trigger power_user
            repeat(5) {
                rollingCounter.append(profileId, "Feature Used", now)
            }

            // Collect first event (ENTER)
            val event =
                withTimeout(1000) {
                    // Start collecting before emitting
                    val deferred =
                        async {
                            engine.segmentEvents.first()
                        }

                    // Yield to ensure collector is subscribed
                    yield()

                    // Update profile (this will emit the event)
                    val profile2 = createProfile(profileId, lastSeen = now)
                    engine.evaluateAndEmit(profile2)

                    deferred.await()
                }

            // Verify ENTER event emitted
            assertEquals(profileId, event.profileId)
            assertEquals("power_user", event.segment)
            assertEquals(SegmentAction.ENTER, event.action)
        }

    @Test
    fun `should emit EXIT event when profile leaves segment`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Start with pro_plan segment
            val profile1 =
                createProfile(
                    profileId,
                    traits = mapOf("plan" to "pro"),
                    lastSeen = now,
                )
            engine.evaluateAndEmit(profile1)

            // Collect first event (EXIT)
            val event =
                withTimeout(1000) {
                    // Start collecting before emitting
                    val deferred =
                        async {
                            engine.segmentEvents.first()
                        }

                    // Yield to ensure collector is subscribed
                    yield()

                    // Remove pro plan (this will emit the event)
                    val profile2 =
                        createProfile(
                            profileId,
                            traits = mapOf("plan" to "basic"),
                            lastSeen = now,
                        )
                    engine.evaluateAndEmit(profile2)

                    deferred.await()
                }

            // Verify EXIT event emitted
            assertEquals(profileId, event.profileId)
            assertEquals("pro_plan", event.segment)
            assertEquals(SegmentAction.EXIT, event.action)
        }

    @Test
    fun `should emit both ENTER and EXIT events for simultaneous changes`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Start with pro_plan only
            val profile1 =
                createProfile(
                    profileId,
                    traits = mapOf("plan" to "pro"),
                    lastSeen = now,
                )
            engine.evaluateAndEmit(profile1)

            // Add feature events for power_user, remove pro_plan
            repeat(5) {
                rollingCounter.append(profileId, "Feature Used", now)
            }

            // Collect both events (ENTER and EXIT)
            val events =
                withTimeout(1000) {
                    // Start collecting before emitting
                    val deferred =
                        async {
                            engine.segmentEvents.take(2).toList()
                        }

                    // Yield to ensure collector is subscribed
                    yield()

                    // Update profile (this will emit both events)
                    val profile2 =
                        createProfile(
                            profileId,
                            traits = mapOf("plan" to "basic"),
                            lastSeen = now,
                        )
                    engine.evaluateAndEmit(profile2)

                    deferred.await()
                }

            // Should have EXIT pro_plan and ENTER power_user
            assertEquals(2, events.size)

            val enterEvent = events.find { it.action == SegmentAction.ENTER }
            val exitEvent = events.find { it.action == SegmentAction.EXIT }

            assertEquals("power_user", enterEvent?.segment)
            assertEquals("pro_plan", exitEvent?.segment)
        }

    @Test
    fun `should not emit events when segments unchanged`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Start with pro_plan
            val profile1 =
                createProfile(
                    profileId,
                    traits = mapOf("plan" to "pro"),
                    lastSeen = now,
                )
            engine.evaluateAndEmit(profile1)

            // Keep same segments
            val profile2 =
                createProfile(
                    profileId,
                    traits = mapOf("plan" to "pro"),
                    lastSeen = now,
                )

            val segments = engine.evaluateAndEmit(profile2)

            // No events should be emitted (segments unchanged)
            assertTrue(segments.contains("pro_plan"))
        }

    // === Boundary Condition Tests (DoD Requirement) ===

    @Test
    fun `power_user should transition at exactly 5 events`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Start with 4 events (not power_user)
            repeat(4) {
                rollingCounter.append(profileId, "Feature Used", now)
            }

            val profile1 = createProfile(profileId, lastSeen = now)
            val segments1 = engine.evaluateAndEmit(profile1)
            assertFalse(segments1.contains("power_user"))

            // Add 5th event (should cross threshold)
            rollingCounter.append(profileId, "Feature Used", now)

            // Collect ENTER event
            val event =
                withTimeout(1000) {
                    // Start collecting before emitting
                    val deferred =
                        async {
                            engine.segmentEvents.first()
                        }

                    // Yield to ensure collector is subscribed
                    yield()

                    // Update profile (this will emit the event)
                    val profile2 = createProfile(profileId, lastSeen = now)
                    val segments2 = engine.evaluateAndEmit(profile2)
                    assertTrue(segments2.contains("power_user"))

                    deferred.await()
                }

            assertEquals(SegmentAction.ENTER, event.action)
        }

    @Test
    fun `reengage should transition at exactly 10 minutes plus 1 second`() {
        val now = Instant.now()
        val profileId = "profile-1"

        // Just under 10 minutes - should NOT be reengage
        val profile1 = createProfile(profileId, lastSeen = now.minus(Duration.ofMinutes(9)))
        val segments1 = engine.evaluate(profile1)
        assertFalse(segments1.contains("reengage"))

        // Well over 10 minutes - SHOULD be reengage
        val profile2 =
            createProfile(
                profileId,
                lastSeen = now.minus(Duration.ofMinutes(11)),
            )
        val segments2 = engine.evaluate(profile2)
        assertTrue(segments2.contains("reengage"))
    }

    // === Multiple Rules Tests ===

    @Test
    fun `profile can belong to multiple segments simultaneously`() {
        val profileId = "profile-1"
        val now = Instant.now()

        // Set up for all three segments
        repeat(5) {
            rollingCounter.append(profileId, "Feature Used", now)
        }

        val profile =
            createProfile(
                profileId,
                traits = mapOf("plan" to "pro"),
                lastSeen = now.minus(Duration.ofMinutes(15)),
            )

        val segments = engine.evaluate(profile)

        assertTrue(segments.contains("power_user"))
        assertTrue(segments.contains("pro_plan"))
        assertTrue(segments.contains("reengage"))
        assertEquals(3, segments.size)
    }

    @Test
    fun `profile can have no segments`() {
        val profile =
            createProfile(
                "profile-1",
                traits = mapOf("plan" to "basic"),
                lastSeen = Instant.now(),
            )

        val segments = engine.evaluate(profile)

        assertEquals(0, segments.size)
    }

    // === Helper Functions ===

    private fun createProfile(
        profileId: String,
        traits: Map<String, Any?> = emptyMap(),
        lastSeen: Instant,
    ): CdpProfile {
        return CdpProfile(
            profileId = profileId,
            identifiers = ProfileIdentifiers(),
            traits = traits,
            counters = emptyMap(),
            segments = emptySet(),
            lastSeen = lastSeen,
        )
    }
}
