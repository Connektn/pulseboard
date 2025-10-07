package com.pulseboard.cdp.model

import com.pulseboard.core.BaseEvent
import com.pulseboard.core.Payload
import java.time.Instant

/**
 * CDP Event payload model for IDENTIFY, TRACK, or ALIAS events.
 */
data class CdpEventPayload(
    val type: CdpEventType,
    val anonymousId: String? = null,
    val userId: String? = null,
    val email: String? = null,
    val name: String? = null,
    val properties: Map<String, Any?> = emptyMap(),
    val traits: Map<String, Any?> = emptyMap(),
) : Payload

/**
 * CDP Event model representing IDENTIFY, TRACK, or ALIAS events.
 */
class CdpEvent(
    eventId: String,
    ts: Instant,
    payload: CdpEventPayload,
) : BaseEvent<CdpEventPayload>(eventId, ts, payload) {
    fun key(): String {
        return payload.userId ?: payload.anonymousId ?: eventId
    }

    /**
     * Validates the event according to CDP requirements.
     * Should be called explicitly after deserialization.
     */
    fun validate() {
        require(eventId.isNotBlank()) { "eventId is required" }
        require(payload.anonymousId != null || payload.userId != null || payload.email != null) {
            "At least one of anonymousId, userId, or email must be present"
        }
        if (payload.type == CdpEventType.TRACK) {
            require(!payload.name.isNullOrBlank()) { "TRACK events require a name" }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CdpEvent) return false
        if (eventId != other.eventId) return false
        if (ts != other.ts) return false
        if (payload != other.payload) return false
        return true
    }

    override fun hashCode(): Int {
        var result = eventId.hashCode()
        result = 31 * result + ts.hashCode()
        result = 31 * result + payload.hashCode()
        return result
    }
}

enum class CdpEventType {
    IDENTIFY,
    TRACK,
    ALIAS,
}
