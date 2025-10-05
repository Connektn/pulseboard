package com.pulseboard.cdp.store

import com.pulseboard.cdp.model.CdpProfile
import com.pulseboard.cdp.model.ProfileIdentifiers
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store for CDP profiles with LWW (Last-Write-Wins) trait merging.
 *
 * Features:
 * - Store profiles by canonical profileId
 * - Merge identifiers (union of all identifier sets)
 * - LWW trait merging based on event timestamp
 * - Update counters and segments
 */
@Component
class ProfileStore {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Profile storage by canonical profileId
    private val profiles = ConcurrentHashMap<String, CdpProfile>()

    // Trait timestamps for LWW (Last-Write-Wins) by profileId -> traitKey -> timestamp
    private val traitTimestamps = ConcurrentHashMap<String, MutableMap<String, Instant>>()

    /**
     * Get a profile by canonical profileId.
     */
    fun get(profileId: String): CdpProfile? {
        return profiles[profileId]
    }

    /**
     * Get or create a profile with default values.
     */
    fun getOrCreate(profileId: String): CdpProfile {
        return profiles.computeIfAbsent(profileId) {
            CdpProfile(
                profileId = profileId,
                identifiers = ProfileIdentifiers(),
                traits = emptyMap(),
                counters = emptyMap(),
                segments = emptySet(),
                lastSeen = Instant.EPOCH,
            )
        }
    }

    /**
     * Update profile identifiers by merging with existing identifiers.
     */
    fun mergeIdentifiers(
        profileId: String,
        newIdentifiers: ProfileIdentifiers,
    ): CdpProfile {
        val current = getOrCreate(profileId)
        val merged =
            ProfileIdentifiers(
                userIds = current.identifiers.userIds + newIdentifiers.userIds,
                emails = current.identifiers.emails + newIdentifiers.emails,
                anonymousIds = current.identifiers.anonymousIds + newIdentifiers.anonymousIds,
            )

        val updated = current.copy(identifiers = merged)
        profiles[profileId] = updated

        logger.debug(
            "Merged identifiers for profileId={}: userIds={}, emails={}, anonymousIds={}",
            profileId,
            merged.userIds.size,
            merged.emails.size,
            merged.anonymousIds.size,
        )

        return updated
    }

    /**
     * Merge traits using Last-Write-Wins (LWW) strategy.
     * Only update traits if the event timestamp is newer than the last update for that trait.
     */
    fun mergeTraits(
        profileId: String,
        newTraits: Map<String, Any?>,
        eventTimestamp: Instant,
    ): CdpProfile {
        val current = getOrCreate(profileId)
        val timestamps = traitTimestamps.computeIfAbsent(profileId) { ConcurrentHashMap() }

        val mergedTraits = current.traits.toMutableMap()

        newTraits.forEach { (key, value) ->
            val lastUpdateTime = timestamps[key]
            if (lastUpdateTime == null || eventTimestamp >= lastUpdateTime) {
                mergedTraits[key] = value
                timestamps[key] = eventTimestamp
                logger.debug(
                    "Updated trait for profileId={}: key={}, value={}, ts={}",
                    profileId,
                    key,
                    value,
                    eventTimestamp,
                )
            } else {
                logger.debug(
                    "Skipped stale trait update for profileId={}: key={}, eventTs={}, lastTs={}",
                    profileId,
                    key,
                    eventTimestamp,
                    lastUpdateTime,
                )
            }
        }

        val updated = current.copy(traits = mergedTraits)
        profiles[profileId] = updated

        return updated
    }

    /**
     * Update lastSeen timestamp if newer.
     */
    fun updateLastSeen(
        profileId: String,
        timestamp: Instant,
    ): CdpProfile {
        val current = getOrCreate(profileId)
        if (timestamp > current.lastSeen) {
            val updated = current.copy(lastSeen = timestamp)
            profiles[profileId] = updated
            logger.debug("Updated lastSeen for profileId={}: {}", profileId, timestamp)
            return updated
        }
        return current
    }

    /**
     * Update counters for a profile.
     */
    fun updateCounters(
        profileId: String,
        counters: Map<String, Long>,
    ): CdpProfile {
        val current = getOrCreate(profileId)
        val updated = current.copy(counters = counters)
        profiles[profileId] = updated
        return updated
    }

    /**
     * Update segments for a profile.
     */
    fun updateSegments(
        profileId: String,
        segments: Set<String>,
    ): CdpProfile {
        val current = getOrCreate(profileId)
        val updated = current.copy(segments = segments)
        profiles[profileId] = updated
        logger.debug("Updated segments for profileId={}: {}", profileId, segments)
        return updated
    }

    /**
     * Get all profiles (for testing).
     */
    fun getAll(): Map<String, CdpProfile> {
        return profiles.toMap()
    }

    /**
     * Clear all state (for testing).
     */
    fun clear() {
        profiles.clear()
        traitTimestamps.clear()
        logger.debug("ProfileStore cleared")
    }

    /**
     * Get profile count.
     */
    fun size(): Int = profiles.size
}
