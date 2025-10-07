package com.pulseboard.transport

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.ingest.CdpEventBus
import kotlinx.coroutines.flow.Flow
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * In-memory event transport using existing EventBus.
 * This is the default transport mode for simplifying demo setup.
 */
@Component
@ConditionalOnProperty(value = ["transport.mode"], havingValue = "memory", matchIfMissing = true)
class MemoryCdpEventTransport(
    private val eventBus: CdpEventBus,
) : CdpEventTransport {
    override suspend fun publishEvent(event: CdpEvent) {
        eventBus.publishEvent(event)
    }

    override fun subscribeToEvents(): Flow<CdpEvent> {
        return eventBus.events
    }

    override fun getTransportType(): TransportType = TransportType.MEMORY
}
