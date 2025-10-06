package com.pulseboard.core

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * Marker interface for event payloads.
 */
interface Payload

/**
 * Base class for core events with a generic payload.
 */
abstract class BaseEvent<P : Payload>(
    val eventId: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val ts: Instant,
    val payload: P,
)
