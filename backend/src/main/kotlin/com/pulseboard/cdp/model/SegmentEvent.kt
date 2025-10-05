package com.pulseboard.cdp.model

import java.time.Instant

/**
 * Segment event representing a profile entering or exiting a segment.
 */
data class SegmentEvent(
    val profileId: String,
    val segment: String,
    val action: SegmentAction,
    val ts: Instant
)

/**
 * Action type for segment events.
 */
enum class SegmentAction {
    ENTER,
    EXIT
}
