package com.pulseboard.cdp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.cdp.model.CdpEventType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant

class CdpIngestControllerTest {
    private lateinit var mockEventBus: CdpEventBus
    private lateinit var objectMapper: ObjectMapper
    private lateinit var controller: CdpIngestController
    private lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setup() {
        mockEventBus = mockk()
        objectMapper = ObjectMapper().findAndRegisterModules()
        controller = CdpIngestController(mockEventBus)
        webTestClient = WebTestClient.bindToController(controller).build()

        // Default behavior: allow event publishing
        coEvery { mockEventBus.publish(any()) } returns Unit
    }

    @Test
    fun `should accept valid TRACK event and return 202`() {
        val eventJson =
            """
            {
                "eventId": "evt-123",
                "ts": "2025-10-04T10:30:00Z",
                "type": "TRACK",
                "userId": "user-123",
                "name": "Feature Used",
                "properties": {"feature": "dashboard"}
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isAccepted
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("accepted")
            .jsonPath("$.eventId")
            .isEqualTo("evt-123")

        coVerify { mockEventBus.publish(any()) }
    }

    @Test
    fun `should accept valid IDENTIFY event with minimal fields`() {
        val eventJson =
            """
            {
                "eventId": "evt-456",
                "ts": "2025-10-04T11:00:00Z",
                "type": "IDENTIFY",
                "email": "user@example.com"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isAccepted
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("accepted")

        coVerify { mockEventBus.publish(any()) }
    }

    @Test
    fun `should accept valid ALIAS event`() {
        val eventJson =
            """
            {
                "eventId": "evt-789",
                "ts": "2025-10-04T12:00:00Z",
                "type": "ALIAS",
                "anonymousId": "anon-123",
                "userId": "user-456"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isAccepted
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("accepted")

        coVerify { mockEventBus.publish(any()) }
    }

    @Test
    fun `should reject TRACK event without name`() {
        val eventJson =
            """
            {
                "eventId": "evt-invalid",
                "ts": "2025-10-04T10:30:00Z",
                "type": "TRACK",
                "userId": "user-123"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .value<String> { it.contains("TRACK events require a name") }

        coVerify(exactly = 0) { mockEventBus.publish(any()) }
    }

    @Test
    fun `should reject event without any identifier`() {
        val eventJson =
            """
            {
                "eventId": "evt-invalid",
                "ts": "2025-10-04T10:30:00Z",
                "type": "IDENTIFY"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .value<String> { it.contains("At least one of") }

        coVerify(exactly = 0) { mockEventBus.publish(any()) }
    }

    @Test
    fun `should reject event with empty eventId`() {
        val eventJson =
            """
            {
                "eventId": "",
                "ts": "2025-10-04T10:30:00Z",
                "type": "IDENTIFY",
                "userId": "user-123"
            }
            """.trimIndent()

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("error")
            .jsonPath("$.message")
            .value<String> { it.contains("eventId is required") }

        coVerify(exactly = 0) { mockEventBus.publish(any()) }
    }

    @Test
    fun `should reject malformed JSON`() {
        val eventJson = """{"eventId": "evt-123", "ts": "invalid"}"""

        webTestClient
            .post()
            .uri("/cdp/ingest")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(eventJson)
            .exchange()
            .expectStatus()
            .is4xxClientError

        coVerify(exactly = 0) { mockEventBus.publish(any()) }
    }

    @Test
    fun `should publish event to bus on successful ingestion`() =
        runBlocking {
            val event =
                CdpEvent(
                    eventId = "evt-success",
                    ts = Instant.parse("2025-10-04T14:00:00Z"),
                    type = CdpEventType.IDENTIFY,
                    userId = "user-999",
                    traits = mapOf("plan" to "pro"),
                )

            coEvery { mockEventBus.publish(event) } returns Unit

            val eventJson = objectMapper.writeValueAsString(event)

            webTestClient
                .post()
                .uri("/cdp/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(eventJson)
                .exchange()
                .expectStatus()
                .isAccepted

            coVerify { mockEventBus.publish(any()) }
        }
}
