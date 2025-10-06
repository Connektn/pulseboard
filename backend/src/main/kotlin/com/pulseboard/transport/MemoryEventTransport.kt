package com.pulseboard.transport

import com.pulseboard.core.EntityEvent
import com.pulseboard.ingest.EventBus
import kotlinx.coroutines.flow.Flow
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * In-memory event transport using existing EventBus.
 * This is the default transport mode for simplifying demo setup.
 */
@Component
@ConditionalOnProperty(value = ["transport.mode"], havingValue = "memory", matchIfMissing = true)
class MemoryEventTransport(
    private val eventBus: EventBus,
) : EventTransport {
    override suspend fun publishEvent(event: EntityEvent) {
        eventBus.publishEvent(event)
    }

    override fun subscribeToEvents(): Flow<EntityEvent> {
        return eventBus.events
    }

    override fun getTransportType(): TransportType = TransportType.MEMORY
}
