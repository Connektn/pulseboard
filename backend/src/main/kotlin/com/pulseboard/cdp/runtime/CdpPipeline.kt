package com.pulseboard.cdp.runtime

import com.pulseboard.cdp.api.CdpEventBus
import com.pulseboard.cdp.identity.IdentityGraph
import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.cdp.model.CdpEventType
import com.pulseboard.cdp.model.ProfileIdentifiers
import com.pulseboard.cdp.segments.SegmentEngine
import com.pulseboard.cdp.store.ProfileStore
import com.pulseboard.cdp.store.RollingCounter
import io.micrometer.core.instrument.MeterRegistry
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * CDP Processing Pipeline that wires together all components.
 *
 * Flow: CdpBus.events → identity resolution → buffer/watermark → profile store → segment engine
 *
 * Steps for each event:
 * 1. Subscribe to CdpBus.events
 * 2. Normalize identifiers (user:, email:, anon: prefixes)
 * 3. On IDENTIFY/ALIAS: call IdentityGraph.union() for identifier pairs
 * 4. Compute canonicalId = IdentityGraph.canonicalIdFor(ids)
 * 5. Enqueue into CdpEventProcessor (K4 buffer)
 * 6. On drain (≤ watermark): process in order
 *    - Merge identifiers
 *    - Merge traits (LWW)
 *    - Update lastSeen
 *    - RollingCounter.increment for TRACK events
 *    - SegmentEngine.evaluateAndEmit() → emits ENTER/EXIT events
 */
@Component
class CdpPipeline(
    private val eventBus: CdpEventBus,
    private val identityGraph: IdentityGraph,
    private val profileStore: ProfileStore,
    private val rollingCounter: RollingCounter,
    private val segmentEngine: SegmentEngine,
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.Default)

    // Event processor with watermark and buffering
    private val eventProcessor = CdpEventProcessor(meterRegistry = meterRegistry)

    @PostConstruct
    fun start() {
        logger.info("Starting CDP pipeline")

        // Subscribe to CDP event bus
        eventBus.events
            .onEach { event ->
                try {
                    // Ensure the event is valid
                    event.validate()
                    // Process ingestion steps
                    ingestEvent(event)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Validation failed for event: eventId={}, error={}", event.eventId, e.message)
                } catch (e: Exception) {
                    logger.error("Error ingesting event: eventId={}", event.eventId, e)
                }
            }
            .launchIn(scope)

        // Register event handler for processed events
        eventProcessor.onEvent { event ->
            try {
                runBlocking {
                    processEvent(event)
                }
            } catch (e: Exception) {
                logger.error("Error processing event: eventId={}", event.eventId, e)
            }
        }

        // Start the watermark ticker
        eventProcessor.start()

        logger.info("CDP pipeline started successfully")
    }

    @PreDestroy
    fun stop() {
        logger.info("Stopping CDP pipeline")
        eventProcessor.stop()
    }

    /**
     * Step 1-4: Ingest event, normalize identifiers, resolve identity, enqueue.
     */
    private fun ingestEvent(event: CdpEvent) {
        logger.debug("Ingesting event: eventId={}, type={}", event.eventId, event.type)

        // Extract and normalize identifiers
        val identifiers = extractIdentifiers(event)

        // On IDENTIFY/ALIAS: union identifiers in identity graph
        when (event.type) {
            CdpEventType.IDENTIFY, CdpEventType.ALIAS -> {
                unionIdentifiers(identifiers)
            }
            else -> {}
        }

        // Compute canonical profileId
        val canonicalId = identityGraph.canonicalIdFor(identifiers.toList())

        logger.debug("Resolved canonicalId={} for event: eventId={}", canonicalId, event.eventId)

        // Enqueue event into buffer
        eventProcessor.submit(event, canonicalId)
    }

    /**
     * Step 6: Process drained event (≤ watermark) in order.
     */
    private suspend fun processEvent(event: CdpEvent) {
        logger.debug("Processing event: eventId={}, type={}", event.eventId, event.type)

        // Extract and normalize identifiers
        val identifiers = extractIdentifiers(event)
        val canonicalId = identityGraph.canonicalIdFor(identifiers.toList())

        // Merge identifiers
        profileStore.mergeIdentifiers(
            canonicalId,
            ProfileIdentifiers(
                userIds = identifiers.filter { it.startsWith("user:") }.toSet(),
                emails = identifiers.filter { it.startsWith("email:") }.toSet(),
                anonymousIds = identifiers.filter { it.startsWith("anon:") }.toSet(),
            ),
        )

        // Merge traits (LWW)
        if (event.traits.isNotEmpty()) {
            profileStore.mergeTraits(canonicalId, event.traits, event.ts)
        }

        // Update lastSeen
        profileStore.updateLastSeen(canonicalId, event.ts)

        // Increment rolling counter for TRACK events
        if (event.type == CdpEventType.TRACK && event.name != null) {
            rollingCounter.append(canonicalId, event.name, event.ts)
        }

        // Get current profile state
        val profile = profileStore.getOrCreate(canonicalId)

        // Evaluate segments and emit ENTER/EXIT events
        val newSegments = segmentEngine.evaluateAndEmit(profile)

        // Update profile with new segments
        profileStore.updateSegments(canonicalId, newSegments)

        logger.debug(
            "Completed processing event: eventId={}, profileId={}, segments={}",
            event.eventId,
            canonicalId,
            newSegments,
        )
    }

    /**
     * Extract and normalize identifiers from an event.
     * Returns normalized identifiers with prefixes: user:, email:, anon:
     */
    private fun extractIdentifiers(event: CdpEvent): Set<String> {
        val identifiers = mutableSetOf<String>()

        event.userId?.let { identifiers.add(identityGraph.normalize("user:$it")) }
        event.email?.let { identifiers.add(identityGraph.normalize("email:$it")) }
        event.anonymousId?.let { identifiers.add(identityGraph.normalize("anon:$it")) }

        return identifiers
    }

    /**
     * Union identifiers in the identity graph for all pairs.
     */
    private fun unionIdentifiers(identifiers: Set<String>) {
        if (identifiers.size < 2) return

        val idList = identifiers.toList()
        for (i in 0 until idList.size - 1) {
            for (j in i + 1 until idList.size) {
                identityGraph.union(idList[i], idList[j])
            }
        }
    }

    /**
     * Get event processor (for testing).
     */
    fun getEventProcessor(): CdpEventProcessor = eventProcessor
}
