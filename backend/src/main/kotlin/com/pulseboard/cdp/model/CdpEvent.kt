package com.pulseboard.cdp.model

import java.time.Instant

/**
 * CDP Event model representing IDENTIFY, TRACK, or ALIAS events.
 */
data class CdpEvent(
    val eventId: String,
    val ts: Instant,
    val type: CdpEventType,
    val anonymousId: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
    val traits: Map<String, Any?> = emptyMap(),
) {
    /**
     * Validates the event according to CDP requirements.
     * Should be called explicitly after deserialization.
     */
    fun validate() {
        require(eventId.isNotBlank()) { "eventId is required" }
        require(anonymousId != null || userId != null || email != null) {
            "At least one of anonymousId, userId, or email must be present"
        }
        if (type == CdpEventType.TRACK) {
            require(!name.isNullOrBlank()) { "TRACK events require a name" }
        }
    }
}

enum class CdpEventType {
    IDENTIFY,
    TRACK,
    ALIAS,
}
