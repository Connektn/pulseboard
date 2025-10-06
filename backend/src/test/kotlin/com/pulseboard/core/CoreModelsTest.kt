package com.pulseboard.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID.randomUUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CoreModelsTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }

    @Test
    fun `Event should serialize and deserialize correctly`() {
        val event =
            EntityEvent(
                eventId = randomUUID().toString(),
                ts = Instant.parse("2023-12-01T10:30:00Z"),
                payload =
                    EntityPayload(
                        entityId = "user123",
                        profile = Profile.SASE,
                        type = "CONN_OPEN",
                        value = 42L,
                        tags = mapOf("geo" to "US", "device" to "mobile"),
                    ),
            )

        val json = objectMapper.writeValueAsString(event)
        assertNotNull(json)

        val deserializedEvent: EntityEvent = objectMapper.readValue(json)
        assertEquals(event, deserializedEvent)
    }

    @Test
    fun `Event should serialize and deserialize with minimal fields`() {
        val event =
            EntityEvent(
                eventId = randomUUID().toString(),
                ts = Instant.parse("2023-12-01T10:30:00Z"),
                payload =
                    EntityPayload(
                        profile = Profile.IGAMING,
                        type = "BET_PLACED",
                        entityId = "user456",
                    ),
            )

        val json = objectMapper.writeValueAsString(event)
        assertNotNull(json)

        val deserializedEvent: EntityEvent = objectMapper.readValue(json)
        assertEquals(event, deserializedEvent)
        assertEquals(null, deserializedEvent.payload.value)
        assertEquals(emptyMap(), deserializedEvent.payload.tags)
    }

    @Test
    fun `Alert should serialize and deserialize correctly`() {
        val alert =
            Alert(
                id = "alert-123",
                ts = Instant.parse("2023-12-01T10:35:00Z"),
                rule = "R1_VELOCITY_SPIKE",
                entityId = "user123",
                severity = Severity.HIGH,
                evidence =
                    mapOf(
                        "rate_now" to 45.5,
                        "avg_5m" to 12.3,
                        "threshold" to 36.9,
                    ),
            )

        val json = objectMapper.writeValueAsString(alert)
        assertNotNull(json)

        val deserializedAlert: Alert = objectMapper.readValue(json)
        assertEquals(alert, deserializedAlert)
    }

    @Test
    fun `Profile enum should serialize and deserialize correctly`() {
        val profiles = listOf(Profile.SASE, Profile.IGAMING)

        profiles.forEach { profile ->
            val json = objectMapper.writeValueAsString(profile)
            assertNotNull(json)

            val deserializedProfile: Profile = objectMapper.readValue(json)
            assertEquals(profile, deserializedProfile)
        }
    }

    @Test
    fun `Severity enum should serialize and deserialize correctly`() {
        val severities = listOf(Severity.LOW, Severity.MEDIUM, Severity.HIGH)

        severities.forEach { severity ->
            val json = objectMapper.writeValueAsString(severity)
            assertNotNull(json)

            val deserializedSeverity: Severity = objectMapper.readValue(json)
            assertEquals(severity, deserializedSeverity)
        }
    }
}
