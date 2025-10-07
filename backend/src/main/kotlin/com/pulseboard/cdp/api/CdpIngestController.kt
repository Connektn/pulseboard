package com.pulseboard.cdp.api

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.ingest.CdpEventBus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for CDP event ingestion.
 */
@RestController
@RequestMapping("/cdp")
class CdpIngestController(
    private val eventBus: CdpEventBus,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/ingest")
    suspend fun ingest(
        @RequestBody event: CdpEvent,
    ): ResponseEntity<Map<String, String>> {
        return try {
            // Validate the event
            event.validate()
            eventBus.publishEvent(event)
            logger.debug("Ingested CDP event: eventId={}, type={}", event.eventId, event.payload.type)
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
