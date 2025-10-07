package com.pulseboard.core

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * Event interface representing a generic event with an ID and timestamp.
 */
interface Event {
    val eventId: String
    val ts: Instant
}

/**
 * Marker interface for event payloads.
 */
interface Payload

/**
 * Base class for core events with a generic payload.
 */
abstract class BaseEvent<P : Payload>(
    override val eventId: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    override val ts: Instant,
    val payload: P,
) : Event
