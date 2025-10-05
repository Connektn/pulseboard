package com.pulseboard.cdp.segments

import com.pulseboard.cdp.model.CdpProfile
import com.pulseboard.cdp.model.SegmentAction
import com.pulseboard.cdp.model.SegmentEvent
import com.pulseboard.cdp.store.RollingCounter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Segment engine for evaluating profile membership in segments.
 *
 * Hardcoded rules:
 * - power_user: TRACK[name="Feature Used"] count ≥ 5 in 24h
 * - pro_plan: trait plan == "pro"
 * - reengage: now - lastSeen > 10m (configurable)
 */
@Component
class SegmentEngine(
    private val rollingCounter: RollingCounter,
    private val reengageInactivityThreshold: Duration = Duration.ofMinutes(10),
    private val powerUserThreshold: Int = 5,
    private val powerUserWindow: Duration = Duration.ofHours(24)
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Shared flow for segment events
    private val _segmentEvents = MutableSharedFlow<SegmentEvent>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    val segmentEvents: SharedFlow<SegmentEvent> = _segmentEvents.asSharedFlow()

    // Track previous segment memberships per profile
    private val previousSegments = mutableMapOf<String, Set<String>>()

    /**
     * Evaluate current segment membership for a profile.
     *
     * @param profile The CDP profile
     * @return Set of segment names the profile belongs to
     */
    fun evaluate(profile: CdpProfile): Set<String> {
        val segments = mutableSetOf<String>()

        // Rule 1: power_user
        if (isPowerUser(profile)) {
            segments.add("power_user")
        }

        // Rule 2: pro_plan
        if (isProPlan(profile)) {
            segments.add("pro_plan")
        }

        // Rule 3: reengage
        if (needsReengagement(profile)) {
            segments.add("reengage")
        }

        logger.debug("Evaluated segments for profileId={}: {}", profile.profileId, segments)
        return segments
    }

    /**
     * Evaluate segments and emit ENTER/EXIT events for changes.
     *
     * @param profile The CDP profile
     * @return The current segment membership
     */
    suspend fun evaluateAndEmit(profile: CdpProfile): Set<String> {
        val currentSegments = evaluate(profile)
        val oldSegments = previousSegments[profile.profileId] ?: emptySet()

        // Compute diff
        val entered = currentSegments - oldSegments
        val exited = oldSegments - currentSegments

        val now = Instant.now()

        // Emit ENTER events
        entered.forEach { segment ->
            val event = SegmentEvent(
                profileId = profile.profileId,
                segment = segment,
                action = SegmentAction.ENTER,
                ts = now
            )
            _segmentEvents.emit(event)
            logger.info("Profile {} ENTERED segment: {}", profile.profileId, segment)
        }

        // Emit EXIT events
        exited.forEach { segment ->
            val event = SegmentEvent(
                profileId = profile.profileId,
                segment = segment,
                action = SegmentAction.EXIT,
                ts = now
            )
            _segmentEvents.emit(event)
            logger.info("Profile {} EXITED segment: {}", profile.profileId, segment)
        }

        // Update previous segments
        previousSegments[profile.profileId] = currentSegments

        return currentSegments
    }

    /**
     * Rule: power_user
     * TRACK[name="Feature Used"] count ≥ 5 in 24h
     */
    private fun isPowerUser(profile: CdpProfile): Boolean {
        val featureUsedCount = rollingCounter.count(
            profile.profileId,
            "Feature Used",
            powerUserWindow
        )
        return featureUsedCount >= powerUserThreshold
    }

    /**
     * Rule: pro_plan
     * trait plan == "pro"
     */
    private fun isProPlan(profile: CdpProfile): Boolean {
        return profile.traits["plan"] == "pro"
    }

    /**
     * Rule: reengage
     * now - lastSeen > 10m (configurable)
     */
    private fun needsReengagement(profile: CdpProfile): Boolean {
        val now = Instant.now()
        val inactivityDuration = Duration.between(profile.lastSeen, now)
        return inactivityDuration > reengageInactivityThreshold
    }

    /**
     * Get previous segments for a profile (for testing).
     */
    fun getPreviousSegments(profileId: String): Set<String> {
        return previousSegments[profileId] ?: emptySet()
    }

    /**
     * Clear all state (for testing).
     */
    fun clear() {
        previousSegments.clear()
    }
}
