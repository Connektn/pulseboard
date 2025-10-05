package com.pulseboard.cdp.model

import java.time.Instant

/**
 * CDP Profile model representing a unified customer profile.
 */
data class CdpProfile(
    val profileId: String,
    val identifiers: ProfileIdentifiers,
    val traits: Map<String, Any?> = emptyMap(),
    val counters: Map<String, Long> = emptyMap(),
    val segments: Set<String> = emptySet(),
    val lastSeen: Instant,
)

/**
 * Profile identifiers grouped by type.
 */
data class ProfileIdentifiers(
    val userIds: Set<String> = emptySet(),
    val emails: Set<String> = emptySet(),
    val anonymousIds: Set<String> = emptySet(),
)
