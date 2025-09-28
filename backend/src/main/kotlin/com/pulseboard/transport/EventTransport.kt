package com.pulseboard.transport

import com.pulseboard.core.Event
import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction for event streaming.
 * Allows switching between in-memory (EventBus) and Kafka-based transport.
 */
interface EventTransport {
    /**
     * Publish an event to the transport layer
     */
    suspend fun publishEvent(event: Event)

    /**
     * Subscribe to events from the transport layer
     */
    fun subscribeToEvents(): Flow<Event>

    /**
     * Get transport type for debugging/monitoring
     */
    fun getTransportType(): TransportType
}

enum class TransportType {
    MEMORY,
    KAFKA,
}
