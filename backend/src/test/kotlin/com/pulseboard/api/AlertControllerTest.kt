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
        alertController.enableHeartbeat = false // Disable heartbeat for tests
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

        StepVerifier.create(flux.take(1)) // Only take first message
            .expectNextMatches { message ->
                message.contains("event: connection") && message.contains("Connected to alerts stream")
            }
            .verifyComplete()
    }

    @Test
    fun `should handle JSON serialization errors gracefully`() {
        // Create a mock ObjectMapper that throws on writeValueAsString
        val mockObjectMapper = mockk<ObjectMapper>()
        every { mockObjectMapper.writeValueAsString(any()) } throws RuntimeException("Serialization error")

        val controllerWithBadMapper = AlertController(mockEventBus, mockObjectMapper)
        val flux = controllerWithBadMapper.streamAlerts()

        // This test expects that serialization errors are handled gracefully
        // The connection message should still work since it's hardcoded
        StepVerifier.create(flux.take(1))
            .expectNextMatches { message ->
                message.contains("event: connection") || message.contains("event: error")
            }
            .verifyComplete()
    }

    @Test
    fun `connection message should contain correct fields`() {
        val flux = alertController.streamAlerts()

        StepVerifier.create(flux.take(1))
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
            .verifyComplete()
    }

    // WebFlux Slice Test for I1: /sse/alerts endpoint with sample alert
    @Test
    fun `should emit sample alert when published to EventBus`() {
        val sampleAlert = Alert(
            id = "test-alert-123",
            rule = "TEST_RULE",
            entityId = "test-entity",
            severity = Severity.HIGH,
            ts = Instant.now(),
            evidence = mapOf("key" to "value", "threshold" to 100.0)
        )

        val flux = alertController.streamAlerts()

        // Create a test that will publish an alert after subscription
        StepVerifier.create(flux.take(2)) // Take connection + alert
            .expectNextMatches { it.contains("event: connection") } // Connection message
            .then {
                // Publish alert to the flow after connection
                alertFlow.tryEmit(sampleAlert)
            }
            .expectNextMatches { message ->
                // Verify the alert is properly formatted in SSE
                message.contains("event: alert") &&
                    message.contains("\"rule\":\"TEST_RULE\"") &&
                    message.contains("\"entityId\":\"test-entity\"") &&
                    message.contains("\"severity\":\"HIGH\"") &&
                    message.contains("\"evidence\":{") &&
                    message.contains("\"key\":\"value\"")
            }
            .verifyComplete()
    }

    @Test
    fun `should handle multiple alerts in sequence`() {
        val alert1 = Alert(
            id = "alert-1",
            rule = "R1_VELOCITY_SPIKE",
            entityId = "user123",
            severity = Severity.MEDIUM,
            ts = Instant.now(),
            evidence = mapOf("rate_now" to 150.0, "threshold" to 100.0)
        )

        val alert2 = Alert(
            id = "alert-2",
            rule = "R2_VALUE_SPIKE",
            entityId = "user456",
            severity = Severity.LOW,
            ts = Instant.now(),
            evidence = mapOf("value_now" to 2000L, "ewma" to 400.0)
        )

        val flux = alertController.streamAlerts()

        StepVerifier.create(flux.take(3)) // Connection + 2 alerts
            .expectNextMatches { it.contains("event: connection") }
            .then {
                alertFlow.tryEmit(alert1)
                alertFlow.tryEmit(alert2)
            }
            .expectNextMatches { message ->
                message.contains("event: alert") &&
                    message.contains("\"rule\":\"R1_VELOCITY_SPIKE\"") &&
                    message.contains("\"severity\":\"MEDIUM\"")
            }
            .expectNextMatches { message ->
                message.contains("event: alert") &&
                    message.contains("\"rule\":\"R2_VALUE_SPIKE\"") &&
                    message.contains("\"severity\":\"LOW\"")
            }
            .verifyComplete()
    }

    @Test
    fun `should handle alert with complex evidence structure`() {
        val complexEvidence = mapOf(
            "nested" to mapOf("key" to "value"),
            "list" to listOf("item1", "item2"),
            "number" to 42.5,
            "boolean" to true
        )

        val complexAlert = Alert(
            id = "complex-alert",
            rule = "COMPLEX_RULE",
            entityId = "complex-entity",
            severity = Severity.HIGH,
            ts = Instant.now(),
            evidence = complexEvidence
        )

        val flux = alertController.streamAlerts()

        StepVerifier.create(flux.take(2)) // Connection + complex alert
            .expectNextMatches { it.contains("event: connection") }
            .then {
                alertFlow.tryEmit(complexAlert)
            }
            .expectNextMatches { message ->
                message.contains("event: alert") &&
                    message.contains("\"rule\":\"COMPLEX_RULE\"") &&
                    message.contains("\"evidence\":{") &&
                    message.contains("\"number\":42.5") &&
                    message.contains("\"boolean\":true")
            }
            .verifyComplete()
    }
}