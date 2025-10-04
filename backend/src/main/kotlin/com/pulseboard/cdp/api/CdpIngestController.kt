package com.pulseboard.cdp.api

import com.pulseboard.cdp.model.CdpEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Shared flow for CDP events.
 */
@Component
class CdpEventBus {
    private val _events = MutableSharedFlow<CdpEvent>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    val events: SharedFlow<CdpEvent> = _events.asSharedFlow()

    suspend fun publish(event: CdpEvent) {
        _events.emit(event)
    }
}

/**
 * REST controller for CDP event ingestion.
 */
@RestController
@RequestMapping("/cdp")
class CdpIngestController(
    private val eventBus: CdpEventBus
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/ingest")
    suspend fun ingest(@RequestBody event: CdpEvent): ResponseEntity<Map<String, String>> {
        return try {
            // Validate the event
            event.validate()
            eventBus.publish(event)
            logger.debug("Ingested CDP event: eventId={}, type={}", event.eventId, event.type)
            ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(mapOf("status" to "accepted", "eventId" to event.eventId))
        } catch (e: IllegalArgumentException) {
            logger.warn("Validation failed for CDP event: {}", e.message)
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(mapOf("status" to "error", "message" to (e.message ?: "Validation failed")))
        } catch (e: Exception) {
            logger.error("Error ingesting CDP event", e)
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("status" to "error", "message" to "Internal server error"))
        }
    }
}
