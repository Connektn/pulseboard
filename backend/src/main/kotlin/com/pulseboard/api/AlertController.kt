package com.pulseboard.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.core.Alert
import com.pulseboard.ingest.EventBus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.reactive.asPublisher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import java.time.Instant

@RestController
class AlertController(
    @Autowired private val eventBus: EventBus,
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(AlertController::class.java)

    @GetMapping("/sse/alerts", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamAlerts(): Flux<String> {
        logger.info("Starting SSE alerts stream")

        return createAlertStream()
            .asPublisher()
            .let { Flux.from(it) }
            .doOnSubscribe { logger.info("Client subscribed to alerts stream") }
            .doOnCancel { logger.info("Client unsubscribed from alerts stream") }
            .doOnError { error -> logger.error("Error in alerts stream", error) }
    }

    private fun createAlertStream(): Flow<String> {
        // Create heartbeat flow that emits every 10 seconds
        val heartbeatFlow =
            flow {
                while (true) {
                    kotlinx.coroutines.delay(10000) // 10 seconds
                    emit(createHeartbeatMessage())
                }
            }

        // Create alert flow from EventBus
        val alertFlow =
            eventBus.alerts
                .map { alert -> createAlertMessage(alert) }
                .catch { error ->
                    logger.error("Error processing alert", error)
                    emit(createErrorMessage("Error processing alert: ${error.message}"))
                }

        // Merge heartbeat and alert flows
        return merge(heartbeatFlow, alertFlow)
            .onStart {
                emit(createConnectionMessage())
            }
            .catch { error ->
                logger.error("Error in alert stream", error)
                emit(createErrorMessage("Stream error: ${error.message}"))
            }
    }

    private fun createAlertMessage(alert: Alert): String {
        return try {
            val message = mapOf(
                "type" to "alert",
                "data" to alert
            )
            objectMapper.writeValueAsString(message)
        } catch (e: Exception) {
            logger.error("Error serializing alert", e)
            createErrorMessage("Error serializing alert: ${e.message}")
        }
    }

    private fun createHeartbeatMessage(): String {
        val heartbeat =
            mapOf(
                "type" to "heartbeat",
                "timestamp" to Instant.now().toString(),
            )
        return try {
            objectMapper.writeValueAsString(heartbeat)
        } catch (e: Exception) {
            logger.error("Error creating heartbeat", e)
            "{\"type\":\"heartbeat\",\"timestamp\":\"${Instant.now()}\"}"
        }
    }

    private fun createConnectionMessage(): String {
        val connection =
            mapOf(
                "type" to "connection",
                "message" to "Connected to alerts stream",
                "timestamp" to Instant.now().toString(),
            )
        return try {
            objectMapper.writeValueAsString(connection)
        } catch (e: Exception) {
            val timestamp = Instant.now()
            "{\"type\":\"connection\",\"message\":\"Connected to alerts stream\",\"timestamp\":\"$timestamp\"}"
        }
    }

    private fun createErrorMessage(message: String): String {
        val error =
            mapOf(
                "type" to "error",
                "message" to message,
                "timestamp" to Instant.now().toString(),
            )
        return try {
            objectMapper.writeValueAsString(error)
        } catch (e: Exception) {
            "{\"type\":\"error\",\"message\":\"$message\",\"timestamp\":\"${Instant.now()}\"}"
        }
    }
}
