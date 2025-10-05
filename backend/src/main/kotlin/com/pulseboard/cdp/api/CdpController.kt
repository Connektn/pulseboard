package com.pulseboard.cdp.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.cdp.model.ProfileSummary
import com.pulseboard.cdp.model.SegmentEvent
import com.pulseboard.cdp.segments.SegmentEngine
import com.pulseboard.cdp.store.ProfileStore
import com.pulseboard.cdp.store.RollingCounter
import kotlinx.coroutines.delay
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
import java.time.Duration
import java.time.Instant

@RestController
class CdpController(
    @Autowired private val segmentEngine: SegmentEngine,
    @Autowired private val profileStore: ProfileStore,
    @Autowired private val rollingCounter: RollingCounter,
    @Autowired private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(CdpController::class.java)
    var enableHeartbeat = true // Allow disabling heartbeat for tests

    // Track previous profile summaries to avoid re-emitting unchanged data
    private var previousProfiles: Map<String, ProfileSummary> = emptyMap()

    @GetMapping("/sse/cdp/segments", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamSegments(): Flux<String> {
        logger.info("Starting SSE segments stream")

        return createSegmentStream()
            .asPublisher()
            .let { Flux.from(it) }
            .doOnSubscribe { logger.info("Client subscribed to segments stream") }
            .doOnCancel { logger.info("Client unsubscribed from segments stream") }
            .doOnError { error -> logger.error("Error in segments stream", error) }
    }

    @GetMapping("/sse/cdp/profiles", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamProfiles(): Flux<String> {
        logger.info("Starting SSE profiles stream")

        return createProfileStream()
            .asPublisher()
            .let { Flux.from(it) }
            .doOnSubscribe { logger.info("Client subscribed to profiles stream") }
            .doOnCancel { logger.info("Client unsubscribed from profiles stream") }
            .doOnError { error -> logger.error("Error in profiles stream", error) }
    }

    private fun createSegmentStream(): Flow<String> {
        // Create segment event flow from SegmentEngine
        val segmentFlow =
            segmentEngine.segmentEvents
                .map { event -> createSegmentEventMessage(event) }
                .catch { error ->
                    logger.error("Error processing segment event", error)
                    emit(createErrorMessage("Error processing segment event: ${error.message}"))
                }

        return if (enableHeartbeat) {
            // Create heartbeat flow that emits every 10 seconds
            val heartbeatFlow =
                flow {
                    while (true) {
                        delay(10000) // 10 seconds
                        emit(createHeartbeatMessage())
                    }
                }

            // Merge heartbeat and segment event flows
            merge(heartbeatFlow, segmentFlow)
                .onStart {
                    emit(createConnectionMessage("segments"))
                }
                .catch { error ->
                    logger.error("Error in segment stream", error)
                    emit(createErrorMessage("Stream error: ${error.message}"))
                }
        } else {
            // Test mode - no heartbeat
            segmentFlow
                .onStart {
                    emit(createConnectionMessage("segments"))
                }
                .catch { error ->
                    logger.error("Error in segment stream", error)
                    emit(createErrorMessage("Stream error: ${error.message}"))
                }
        }
    }

    private fun createProfileStream(): Flow<String> {
        // Create throttled profile summary flow
        val profileFlow =
            flow {
                while (true) {
                    try {
                        val summaries = getCurrentProfileSummaries()

                        // Only emit if changed from previous
                        if (summaries != previousProfiles) {
                            emit(createProfileSummariesMessage(summaries.values.toList()))
                            previousProfiles = summaries
                        }
                    } catch (e: Exception) {
                        logger.error("Error generating profile summaries", e)
                        emit(createErrorMessage("Error generating profile summaries: ${e.message}"))
                    }

                    delay(1000) // Throttle to 1 second
                }
            }
                .catch { error ->
                    logger.error("Error in profile flow", error)
                    emit(createErrorMessage("Profile flow error: ${error.message}"))
                }

        return profileFlow
            .onStart {
                emit(createConnectionMessage("profiles"))
            }
            .catch { error ->
                logger.error("Error in profile stream", error)
                emit(createErrorMessage("Stream error: ${error.message}"))
            }
    }

    /**
     * Get current profile summaries (top 20 by lastSeen).
     */
    private fun getCurrentProfileSummaries(): Map<String, ProfileSummary> {
        return profileStore.getAll()
            .values
            .sortedByDescending { it.lastSeen }
            .take(20)
            .associate { profile ->
                // Strip prefixes from identifiers for UI display
                val cleanIdentifiers =
                    com.pulseboard.cdp.model.ProfileIdentifiers(
                        userIds = profile.identifiers.userIds.map { it.removePrefix("user:") }.toSet(),
                        emails = profile.identifiers.emails.map { it.removePrefix("email:") }.toSet(),
                        anonymousIds = profile.identifiers.anonymousIds.map { it.removePrefix("anon:") }.toSet(),
                    )

                val summary =
                    ProfileSummary(
                        profileId = profile.profileId,
                        plan = profile.traits["plan"] as? String,
                        country = profile.traits["country"] as? String,
                        lastSeen = profile.lastSeen,
                        identifiers = cleanIdentifiers,
                        featureUsedCount =
                            rollingCounter.count(
                                profile.profileId,
                                "Feature Used",
                                Duration.ofHours(24),
                            ),
                    )
                profile.profileId to summary
            }
    }

    private fun createSegmentEventMessage(event: SegmentEvent): String {
        return try {
            val message =
                mapOf(
                    "type" to "segment_event",
                    "data" to event,
                )
            objectMapper.writeValueAsString(message)
        } catch (e: Exception) {
            logger.error("Error serializing segment event", e)
            createErrorMessage("Error serializing segment event: ${e.message}")
        }
    }

    private fun createProfileSummariesMessage(summaries: List<ProfileSummary>): String {
        return try {
            val message =
                mapOf(
                    "type" to "profile_summaries",
                    "data" to summaries,
                )
            objectMapper.writeValueAsString(message)
        } catch (e: Exception) {
            logger.error("Error serializing profile summaries", e)
            createErrorMessage("Error serializing profile summaries: ${e.message}")
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

    private fun createConnectionMessage(streamType: String): String {
        val connection =
            mapOf(
                "type" to "connection",
                "message" to "Connected to $streamType stream",
                "timestamp" to Instant.now().toString(),
            )
        return try {
            objectMapper.writeValueAsString(connection)
        } catch (e: Exception) {
            val timestamp = Instant.now()
            "{\"type\":\"connection\",\"message\":\"Connected to $streamType stream\",\"timestamp\":\"$timestamp\"}"
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
