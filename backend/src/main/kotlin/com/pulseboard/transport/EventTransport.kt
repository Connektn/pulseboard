package com.pulseboard.transport

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.core.EntityEvent
import com.pulseboard.core.Event
import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction for event streaming.
 * Allows switching between in-memory (EventBus) and Kafka-based transport.
 */
sealed interface EventTransport<E : Event> {
    /**
     * Publish an event to the transport layer
     */
    suspend fun publishEvent(event: E)

    /**
     * Subscribe to events from the transport layer
     */
    fun subscribeToEvents(): Flow<E>

    /**
     * Get the transport type for debugging/monitoring
     */
    fun getTransportType(): TransportType
}

interface EntityEventTransport : EventTransport<EntityEvent>

interface CdpEventTransport : EventTransport<CdpEvent>

enum class TransportType {
    MEMORY,
    KAFKA,
}
