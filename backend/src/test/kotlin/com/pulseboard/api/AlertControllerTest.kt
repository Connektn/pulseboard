package com.pulseboard.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.core.Alert
import com.pulseboard.core.Severity
import com.pulseboard.ingest.EventBus
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier
import java.time.Duration
import java.time.Instant

class AlertControllerTest {
    private lateinit var mockEventBus: EventBus
    private lateinit var objectMapper: ObjectMapper
    private lateinit var alertController: AlertController
    private lateinit var webTestClient: WebTestClient
    private lateinit var alertFlow: MutableSharedFlow<Alert>

    @BeforeEach
    fun setup() {
        mockEventBus = mockk()
        objectMapper = ObjectMapper().findAndRegisterModules()
        alertFlow = MutableSharedFlow()

        every { mockEventBus.alerts } returns alertFlow

        alertController = AlertController(mockEventBus, objectMapper)
        webTestClient = WebTestClient.bindToController(alertController).build()
    }

    @Test
    fun `should return SSE stream with correct content type`() {
        webTestClient.get()
            .uri("/sse/alerts")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("text/event-stream")
    }

    @Test
    fun `should emit connection message on stream start`() {
        val flux = alertController.streamAlerts()

        StepVerifier.create(flux)
            .expectNextMatches { message ->
                message.contains("event: connection") && message.contains("Connected to alerts stream")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `should emit heartbeat messages every 10 seconds`() {
        val flux = alertController.streamAlerts()

        StepVerifier.create(flux)
            .expectNextMatches { it.contains("event: connection") } // Connection message
            .expectNextMatches { it.contains("event: heartbeat") } // First heartbeat
            .thenCancel()
            .verify(Duration.ofSeconds(15))
    }

    @Test
    fun `should handle JSON serialization errors gracefully`() {
        // Create a mock ObjectMapper that throws on writeValueAsString
        val mockObjectMapper = mockk<ObjectMapper>()
        every { mockObjectMapper.writeValueAsString(any()) } throws RuntimeException("Serialization error")

        val controllerWithBadMapper = AlertController(mockEventBus, mockObjectMapper)
        val flux = controllerWithBadMapper.streamAlerts()

        StepVerifier.create(flux)
            .expectNextMatches { message ->
                message.contains("event: connection") || message.contains("event: error")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `connection message should contain correct fields`() {
        val flux = alertController.streamAlerts()

        StepVerifier.create(flux)
            .expectNextMatches { message ->
                val lines = message.split("\n")
                val dataLine = lines.find { it.startsWith("data: ") }?.substring(6) // Remove "data: " prefix

                if (dataLine != null) {
                    val connectionData = objectMapper.readValue(dataLine, Map::class.java)
                    connectionData["type"] == "connection" &&
                        connectionData["message"] == "Connected to alerts stream" &&
                        connectionData.containsKey("timestamp")
                } else {
                    false
                }
            }
            .thenCancel()
            .verify(Duration.ofSeconds(5))
    }

    @Test
    fun `heartbeat message should contain correct fields`() {
        val flux = alertController.streamAlerts()

        StepVerifier.create(flux)
            .expectNextMatches { it.contains("event: connection") }
            .expectNextMatches { message ->
                val lines = message.split("\n")
                val dataLine = lines.find { it.startsWith("data: ") }?.substring(6)

                if (dataLine != null) {
                    val heartbeatData = objectMapper.readValue(dataLine, Map::class.java)
                    heartbeatData["type"] == "heartbeat" &&
                        heartbeatData.containsKey("timestamp")
                } else {
                    false
                }
            }
            .thenCancel()
            .verify(Duration.ofSeconds(15))
    }
}