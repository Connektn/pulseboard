package com.pulseboard.cdp.model

import java.time.Instant

/**
 * Profile summary for SSE streaming to UI.
 *
 * Contains a subset of profile data optimized for real-time display:
 * - Core identifiers (profileId, userIds, emails, anonymousIds)
 * - Key traits (plan, country)
 * - Activity metrics (lastSeen, featureUsedCount)
 */
data class ProfileSummary(
    val profileId: String,
    val plan: String?,
    val country: String?,
    val lastSeen: Instant,
    val identifiers: ProfileIdentifiers,
    val featureUsedCount: Long
)
