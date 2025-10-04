package com.pulseboard.cdp.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CdpModelsTest {
    private val objectMapper =
        ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
        }

    @Test
    fun `CdpEvent should serialize and deserialize correctly with all fields`() {
        val event =
            CdpEvent(
                eventId = "evt-123",
                ts = Instant.parse("2025-10-04T10:30:00Z"),
                type = CdpEventType.TRACK,
                anonymousId = "anon-456",
                userId = "user-789",
                email = "test@example.com",
                name = "Feature Used",
                properties = mapOf("feature" to "dashboard", "count" to 5),
                traits = mapOf("plan" to "pro", "country" to "US"),
            )

        val json = objectMapper.writeValueAsString(event)
        assertNotNull(json)

        val deserializedEvent: CdpEvent = objectMapper.readValue(json)
        assertEquals(event, deserializedEvent)
    }

    @Test
    fun `CdpEvent should serialize and deserialize with minimal fields (IDENTIFY)`() {
        val event =
            CdpEvent(
                eventId = "evt-456",
                ts = Instant.parse("2025-10-04T11:00:00Z"),
                type = CdpEventType.IDENTIFY,
                userId = "user-123",
            )

        val json = objectMapper.writeValueAsString(event)
        assertNotNull(json)

        val deserializedEvent: CdpEvent = objectMapper.readValue(json)
        assertEquals(event, deserializedEvent)
        assertEquals(null, deserializedEvent.anonymousId)
        assertEquals(null, deserializedEvent.email)
        assertEquals(null, deserializedEvent.name)
        assertEquals(emptyMap(), deserializedEvent.properties)
        assertEquals(emptyMap(), deserializedEvent.traits)
    }

    @Test
    fun `CdpEvent should serialize and deserialize with ALIAS type`() {
        val event =
            CdpEvent(
                eventId = "evt-789",
                ts = Instant.parse("2025-10-04T12:00:00Z"),
                type = CdpEventType.ALIAS,
                anonymousId = "anon-999",
                userId = "user-888",
            )

        val json = objectMapper.writeValueAsString(event)
        assertNotNull(json)

        val deserializedEvent: CdpEvent = objectMapper.readValue(json)
        assertEquals(event, deserializedEvent)
    }

    @Test
    fun `CdpEvent should accept ISO-8601 timestamp formats`() {
        val jsonWithOffset = """
            {
                "eventId": "evt-001",
                "ts": "2025-10-04T10:30:00.000+00:00",
                "type": "IDENTIFY",
                "userId": "user-001"
            }
        """.trimIndent()

        val event: CdpEvent = objectMapper.readValue(jsonWithOffset)
        assertNotNull(event)
        assertEquals("evt-001", event.eventId)
        assertEquals(CdpEventType.IDENTIFY, event.type)
    }

    @Test
    fun `CdpEvent should require eventId`() {
        val event =
            CdpEvent(
                eventId = "",
                ts = Instant.now(),
                type = CdpEventType.IDENTIFY,
                userId = "user-123",
            )
        val exception = assertThrows<IllegalArgumentException> { event.validate() }
        assertTrue(exception.message!!.contains("eventId is required"))
    }

    @Test
    fun `CdpEvent should require at least one identifier`() {
        val event =
            CdpEvent(
                eventId = "evt-123",
                ts = Instant.now(),
                type = CdpEventType.IDENTIFY,
            )
        val exception = assertThrows<IllegalArgumentException> { event.validate() }
        assertTrue(exception.message!!.contains("At least one of anonymousId, userId, or email"))
    }

    @Test
    fun `CdpEvent TRACK should require name`() {
        val event =
            CdpEvent(
                eventId = "evt-123",
                ts = Instant.now(),
                type = CdpEventType.TRACK,
                userId = "user-123",
            )
        val exception = assertThrows<IllegalArgumentException> { event.validate() }
        assertTrue(exception.message!!.contains("TRACK events require a name"))
    }

    @Test
    fun `CdpProfile should serialize and deserialize correctly`() {
        val profile =
            CdpProfile(
                profileId = "profile-123",
                identifiers =
                    ProfileIdentifiers(
                        userIds = setOf("user-1", "user-2"),
                        emails = setOf("user@example.com"),
                        anonymousIds = setOf("anon-1", "anon-2"),
                    ),
                traits = mapOf("plan" to "pro", "country" to "US"),
                counters = mapOf("Feature Used" to 10L, "Login" to 5L),
                segments = setOf("power_user", "pro_plan"),
                lastSeen = Instant.parse("2025-10-04T14:00:00Z"),
            )

        val json = objectMapper.writeValueAsString(profile)
        assertNotNull(json)

        val deserializedProfile: CdpProfile = objectMapper.readValue(json)
        assertEquals(profile, deserializedProfile)
    }

    @Test
    fun `CdpProfile should serialize and deserialize with minimal fields`() {
        val profile =
            CdpProfile(
                profileId = "profile-456",
                identifiers = ProfileIdentifiers(),
                lastSeen = Instant.parse("2025-10-04T15:00:00Z"),
            )

        val json = objectMapper.writeValueAsString(profile)
        assertNotNull(json)

        val deserializedProfile: CdpProfile = objectMapper.readValue(json)
        assertEquals(profile, deserializedProfile)
        assertEquals(emptySet(), deserializedProfile.identifiers.userIds)
        assertEquals(emptySet(), deserializedProfile.identifiers.emails)
        assertEquals(emptySet(), deserializedProfile.identifiers.anonymousIds)
        assertEquals(emptyMap(), deserializedProfile.traits)
        assertEquals(emptyMap(), deserializedProfile.counters)
        assertEquals(emptySet(), deserializedProfile.segments)
    }

    @Test
    fun `CdpEventType enum should serialize and deserialize correctly`() {
        val types = listOf(CdpEventType.IDENTIFY, CdpEventType.TRACK, CdpEventType.ALIAS)

        types.forEach { type ->
            val json = objectMapper.writeValueAsString(type)
            assertNotNull(json)

            val deserializedType: CdpEventType = objectMapper.readValue(json)
            assertEquals(type, deserializedType)
        }
    }
}
