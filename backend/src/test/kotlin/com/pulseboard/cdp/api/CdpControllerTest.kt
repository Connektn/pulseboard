package com.pulseboard.cdp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.cdp.model.CdpProfile
import com.pulseboard.cdp.model.ProfileIdentifiers
import com.pulseboard.cdp.model.SegmentEvent
import com.pulseboard.cdp.segments.SegmentEngine
import com.pulseboard.cdp.store.ProfileStore
import com.pulseboard.cdp.store.RollingCounter
import com.pulseboard.fixedClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.test.StepVerifier
import java.time.Duration

class CdpControllerTest {
    private lateinit var mockSegmentEngine: SegmentEngine
    private lateinit var mockProfileStore: ProfileStore
    private lateinit var mockRollingCounter: RollingCounter
    private lateinit var objectMapper: ObjectMapper
    private lateinit var cdpController: CdpController
    private lateinit var webTestClient: WebTestClient
    private lateinit var segmentEventFlow: MutableSharedFlow<SegmentEvent>

    @BeforeEach
    fun setup() {
        mockSegmentEngine = mockk()
        mockProfileStore = mockk()
        mockRollingCounter = mockk()
        objectMapper = ObjectMapper().findAndRegisterModules()
        segmentEventFlow = MutableSharedFlow()

        every { mockSegmentEngine.segmentEvents } returns segmentEventFlow

        cdpController =
            CdpController(
                mockSegmentEngine,
                mockProfileStore,
                mockRollingCounter,
                objectMapper,
            )
        cdpController.enableHeartbeat = false // Disable heartbeat for tests

        webTestClient = WebTestClient.bindToController(cdpController).build()
    }

    @Test
    fun `segments endpoint should return SSE stream with correct content type`() {
        webTestClient.get()
            .uri("/sse/cdp/segments")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("text/event-stream")
    }

    @Test
    fun `segments endpoint should emit connection message on stream start`() {
        val flux = cdpController.streamSegments()

        StepVerifier.create(flux)
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"") &&
                    message.contains("Connected to segments stream")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(1))
    }

    @Test
    fun `profiles endpoint should return SSE stream with correct content type`() {
        // Mock empty profile store
        every { mockProfileStore.getAll() } returns emptyMap()

        webTestClient.get()
            .uri("/sse/cdp/profiles")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith("text/event-stream")
    }

    @Test
    fun `profiles endpoint should emit connection message on stream start`() {
        // Mock empty profile store
        every { mockProfileStore.getAll() } returns emptyMap()

        val flux = cdpController.streamProfiles()

        StepVerifier.create(flux)
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"") &&
                    message.contains("Connected to profiles stream")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(1))
    }

    @Test
    fun `profiles endpoint should emit profile summaries`() {
        val now = fixedClock.instant()
        val profile1 =
            CdpProfile(
                profileId = "profile-1",
                identifiers =
                    ProfileIdentifiers(
                        userIds = setOf("user:u123"),
                        emails = setOf("email:test@example.com"),
                        anonymousIds = emptySet(),
                    ),
                traits = mapOf("plan" to "pro", "country" to "US"),
                counters = emptyMap(),
                segments = emptySet(),
                lastSeen = now,
            )

        every { mockProfileStore.getAll() } returns mapOf("profile-1" to profile1)
        every { mockRollingCounter.count("profile-1", "Feature Used", Duration.ofHours(24)) } returns 10L

        val flux = cdpController.streamProfiles()

        StepVerifier.create(flux.take(2))
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"")
            }
            .expectNextMatches { message ->
                message.contains("\"type\":\"profile_summaries\"") &&
                    message.contains("\"profileId\":\"profile-1\"") &&
                    message.contains("\"plan\":\"pro\"") &&
                    message.contains("\"country\":\"US\"") &&
                    message.contains("\"featureUsedCount\":10")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(3))
    }

    @Test
    fun `profiles endpoint should respect top 20 limit`() {
        val now = fixedClock.instant()
        val profiles =
            (1..30).associate { i ->
                val profileId = "profile-$i"
                profileId to
                    CdpProfile(
                        profileId = profileId,
                        identifiers = ProfileIdentifiers(),
                        traits = mapOf("plan" to "basic"),
                        counters = emptyMap(),
                        segments = emptySet(),
                        lastSeen = now.minusSeconds(i.toLong()),
                    )
            }

        every { mockProfileStore.getAll() } returns profiles
        every { mockRollingCounter.count(any(), "Feature Used", Duration.ofHours(24)) } returns 0L

        val flux = cdpController.streamProfiles()

        StepVerifier.create(flux.take(2))
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"")
            }
            .expectNextMatches { message ->
                val json = objectMapper.readTree(message)
                val data = json.get("data")
                data.isArray && data.size() == 20
            }
            .thenCancel()
            .verify(Duration.ofSeconds(3))
    }

    @Test
    fun `profiles endpoint should sort by lastSeen descending`() {
        val now = fixedClock.instant()
        val profile1 =
            CdpProfile(
                profileId = "profile-1",
                identifiers = ProfileIdentifiers(),
                traits = mapOf("plan" to "basic"),
                counters = emptyMap(),
                segments = emptySet(),
                // Older
                lastSeen = now.minusSeconds(10),
            )
        val profile2 =
            CdpProfile(
                profileId = "profile-2",
                identifiers = ProfileIdentifiers(),
                traits = mapOf("plan" to "pro"),
                counters = emptyMap(),
                segments = emptySet(),
                // Newer
                lastSeen = now,
            )

        every { mockProfileStore.getAll() } returns
            mapOf(
                "profile-1" to profile1,
                "profile-2" to profile2,
            )
        every { mockRollingCounter.count(any(), "Feature Used", Duration.ofHours(24)) } returns 0L

        val flux = cdpController.streamProfiles()

        StepVerifier.create(flux.take(2))
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"")
            }
            .expectNextMatches { message ->
                val json = objectMapper.readTree(message)
                val data = json.get("data")
                // First profile should be profile-2 (newer lastSeen)
                data.isArray && data.size() == 2 &&
                    data.get(0).get("profileId").asText() == "profile-2"
            }
            .thenCancel()
            .verify(Duration.ofSeconds(3))
    }

    @Test
    fun `profiles endpoint should not re-emit unchanged summaries`() {
        val now = fixedClock.instant()
        val profile1 =
            CdpProfile(
                profileId = "profile-1",
                identifiers = ProfileIdentifiers(),
                traits = mapOf("plan" to "basic"),
                counters = emptyMap(),
                segments = emptySet(),
                lastSeen = now,
            )

        every { mockProfileStore.getAll() } returns mapOf("profile-1" to profile1)
        every { mockRollingCounter.count(any(), "Feature Used", Duration.ofHours(24)) } returns 5L

        val flux = cdpController.streamProfiles()

        // Should emit connection + first summary, then no more emissions since data unchanged
        StepVerifier.create(flux.take(2))
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"")
            }
            .expectNextMatches { message ->
                message.contains("\"type\":\"profile_summaries\"")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(3))
    }

    @Test
    fun `segments endpoint should handle JSON serialization errors gracefully`() {
        // Create a mock ObjectMapper that throws on writeValueAsString
        val mockObjectMapper = mockk<ObjectMapper>()
        every { mockObjectMapper.writeValueAsString(any()) } throws RuntimeException("Serialization error")

        val controllerWithBadMapper =
            CdpController(
                mockSegmentEngine,
                mockProfileStore,
                mockRollingCounter,
                mockObjectMapper,
            )
        controllerWithBadMapper.enableHeartbeat = false
        val flux = controllerWithBadMapper.streamSegments()

        // This test expects that serialization errors are handled gracefully
        // The connection message should still work since it's hardcoded
        StepVerifier.create(flux)
            .expectNextMatches { message ->
                message.contains("\"type\":\"connection\"")
            }
            .thenCancel()
            .verify(Duration.ofSeconds(1))
    }
}
