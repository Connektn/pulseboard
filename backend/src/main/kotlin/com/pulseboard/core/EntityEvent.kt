package com.pulseboard.core

import java.time.Instant

data class EntityPayload(
    val entityId: String,
    val profile: Profile,
    val type: String,
    val value: Long? = null,
    val tags: Map<String, String> = emptyMap(),
) : Payload

class EntityEvent(
    eventId: String,
    ts: Instant,
    payload: EntityPayload,
) : BaseEvent<EntityPayload>(eventId, ts, payload) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityEvent) return false
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

    override fun toString(): String {
        return "EntityEvent(eventId='$eventId', ts=$ts, payload=$payload)"
    }
}
