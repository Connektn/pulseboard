package com.pulseboard.cdp.runtime

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.pulseboard.cdp.model.CdpEvent
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.PriorityQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Event processor with dual watermark-based out-of-order handling and deduplication.
 *
 * Features:
 * - Per-profile priority queue (min-heap by timestamp)
 * - Two-tier watermark system:
 *   * Processing watermark (processingWindow=5s) - for micro-batching under high load
 *   * Late event watermark (lateEventGracePeriod=120s) - for handling severely late events
 * - Global ticker (1s) to drain and process events <= watermark
 * - Deduplication via Caffeine cache (TTL=10m per profile)
 * - Micrometer metrics
 *
 * Design for 10k+ events/sec workload:
 * - Events within 5s of current time are processed immediately (micro-batching)
 * - Events older than 5s but within 120s grace period are accepted but logged as late
 * - Events older than 120s are rejected as too late
 */
@Component
class CdpEventProcessor(
    @Value("\${event-processor.window-size:5s}") private val processingWindow: Duration,
    @Value("\${event-processor.grace-period:2m}") private val lateEventGracePeriod: Duration,
    @Value("\${event-processor.dedup-ttl:10m}") private val dedupTtl: Duration,
    @Value("\${event-processor.ticker-interval:1s}") private val tickerInterval: Duration,
    private val clock: Clock,
    meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Per-profile priority queues (min-heap by timestamp)
    private val eventQueues = ConcurrentHashMap<String, PriorityQueue<CdpEvent>>()

    // Per-profile deduplication caches
    private val dedupCaches = ConcurrentHashMap<String, Cache<String, Boolean>>()

    // Watermark state - uses a processing window for micro-batching
    @Volatile
    private var currentWatermark: Instant = clock.instant().minus(processingWindow)

    // Metrics
    private val bufferedEventsGauge = AtomicLong(0)
    private val processedCounter = AtomicLong(0)
    private val dedupHitsCounter = AtomicLong(0)
    private val lateEventsCounter = AtomicLong(0)
    private val droppedEventsCounter = AtomicLong(0)
    private val watermarkLagGauge = AtomicLong(0)

    // Processed event handler
    private var eventHandler: ((CdpEvent) -> Unit)? = null

    // Ticker job
    private var tickerJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        // Register metrics if registry provided
        Gauge.builder("cdp.events.buffered", bufferedEventsGauge) { it.get().toDouble() }
            .description("Number of events currently buffered")
            .register(meterRegistry)

        Counter.builder("cdp.events.processed")
            .description("Total number of events processed")
            .register(meterRegistry)
            .also { counter ->
                // Sync counter with internal counter
                scope.launch {
                    var lastValue = 0L
                    while (isActive) {
                        val currentValue = processedCounter.get()
                        val delta = currentValue - lastValue
                        if (delta > 0) {
                            counter.increment(delta.toDouble())
                            lastValue = currentValue
                        }
                        delay(1000)
                    }
                }
            }

        Counter.builder("cdp.events.dedup_hits")
            .description("Number of duplicate events detected")
            .register(meterRegistry)
            .also { counter ->
                scope.launch {
                    var lastValue = 0L
                    while (isActive) {
                        val currentValue = dedupHitsCounter.get()
                        val delta = currentValue - lastValue
                        if (delta > 0) {
                            counter.increment(delta.toDouble())
                            lastValue = currentValue
                        }
                        delay(1000)
                    }
                }
            }

        Counter.builder("cdp.events.late")
            .description("Number of late events (beyond processing window but within grace period)")
            .register(meterRegistry)
            .also { counter ->
                scope.launch {
                    var lastValue = 0L
                    while (isActive) {
                        val currentValue = lateEventsCounter.get()
                        val delta = currentValue - lastValue
                        if (delta > 0) {
                            counter.increment(delta.toDouble())
                            lastValue = currentValue
                        }
                        delay(1000)
                    }
                }
            }

        Counter.builder("cdp.events.dropped")
            .description("Number of events dropped (beyond grace period)")
            .register(meterRegistry)
            .also { counter ->
                scope.launch {
                    var lastValue = 0L
                    while (isActive) {
                        val currentValue = droppedEventsCounter.get()
                        val delta = currentValue - lastValue
                        if (delta > 0) {
                            counter.increment(delta.toDouble())
                            lastValue = currentValue
                        }
                        delay(1000)
                    }
                }
            }

        Gauge.builder("cdp.watermark.lag_ms", watermarkLagGauge) { it.get().toDouble() }
            .description("Watermark lag in milliseconds (processing window)")
            .register(meterRegistry)
    }

    /**
     * Set the event handler to be called for each processed event.
     */
    fun onEvent(handler: (CdpEvent) -> Unit) {
        this.eventHandler = handler
    }

    /**
     * Submit an event for processing.
     *
     * Two-tier acceptance policy:
     * 1. Events within processingWindow (5s): Buffered for micro-batching
     * 2. Events within lateEventGracePeriod (120s): Accepted as late events
     * 3. Events beyond grace period: Rejected and dropped
     */
    fun submit(
        event: CdpEvent,
        profileId: String,
    ) {
        // Check for duplicate
        val dedupCache = getDedupCache(profileId)
        if (dedupCache.getIfPresent(event.eventId) != null) {
            dedupHitsCounter.incrementAndGet()
            logger.debug("Duplicate event detected: eventId={}, profileId={}", event.eventId, profileId)
            return
        }

        // Check if event is too late (beyond grace period)
        val now = clock.instant()
        val lateEventCutoff = now.minus(lateEventGracePeriod)

        if (event.ts.isBefore(lateEventCutoff)) {
            droppedEventsCounter.incrementAndGet()
            logger.warn(
                "Event dropped (too late): eventId={}, profileId={}, eventTs={}, cutoff={}, lateness={}s",
                event.eventId,
                profileId,
                event.ts,
                lateEventCutoff,
                Duration.between(event.ts, now).seconds,
            )
            return
        }

        // Check if event is late (beyond processing window but within grace period)
        val processingCutoff = now.minus(processingWindow)
        if (event.ts.isBefore(processingCutoff)) {
            lateEventsCounter.incrementAndGet()
            logger.info(
                "Late event accepted: eventId={}, profileId={}, eventTs={}, lateness={}s",
                event.eventId,
                profileId,
                event.ts,
                Duration.between(event.ts, now).seconds,
            )
        }

        // Mark as seen
        dedupCache.put(event.eventId, true)

        // Add to priority queue
        val queue = getOrCreateQueue(profileId)
        synchronized(queue) {
            queue.offer(event)
            bufferedEventsGauge.incrementAndGet()
        }

        logger.debug("Event buffered: eventId={}, profileId={}, ts={}", event.eventId, profileId, event.ts)
    }

    /**
     * Start the watermark ticker.
     * The ticker advances the watermark and drains processable events.
     */
    fun start() {
        if (tickerJob?.isActive == true) {
            logger.warn("Ticker already running")
            return
        }

        tickerJob =
            scope.launch {
                logger.info("Starting watermark ticker with interval={}", tickerInterval)
                while (isActive) {
                    try {
                        tick()
                        delay(tickerInterval.toMillis())
                    } catch (e: Exception) {
                        logger.error("Error in watermark ticker", e)
                    }
                }
            }
    }

    /**
     * Stop the watermark ticker.
     */
    fun stop() {
        tickerJob?.cancel()
        tickerJob = null
        logger.info("Watermark ticker stopped")
    }

    /**
     * Perform a single tick: advance watermark and drain events.
     * Uses processingWindow for micro-batching (5s lag).
     */
    fun tick() {
        // Advance watermark using processing window for micro-batching
        val now = clock.instant()
        currentWatermark = now.minus(processingWindow)

        // Update watermark lag metric (should be ~5000ms for processing window)
        val lag = Duration.between(currentWatermark, now).toMillis()
        watermarkLagGauge.set(lag)

        logger.debug("Watermark advanced to: {} (lag={}ms)", currentWatermark, lag)

        // Drain events from all queues
        eventQueues.forEach { (profileId, queue) ->
            drainQueue(profileId, queue)
        }
    }

    /**
     * Drain events from a queue that are <= watermark.
     */
    private fun drainQueue(
        profileId: String,
        queue: PriorityQueue<CdpEvent>,
    ) {
        val drained = mutableListOf<CdpEvent>()

        synchronized(queue) {
            while (queue.isNotEmpty()) {
                val event = queue.peek()
                if (event.ts <= currentWatermark) {
                    queue.poll()
                    drained.add(event)
                    bufferedEventsGauge.decrementAndGet()
                } else {
                    break
                }
            }
        }

        // Process drained events in order
        drained.forEach { event ->
            processEvent(event, profileId)
        }

        if (drained.isNotEmpty()) {
            logger.debug("Drained {} events for profileId={}", drained.size, profileId)
        }
    }

    /**
     * Process a single event.
     */
    private fun processEvent(
        event: CdpEvent,
        profileId: String,
    ) {
        processedCounter.incrementAndGet()
        logger.debug("Processing event: eventId={}, profileId={}, ts={}", event.eventId, profileId, event.ts)

        // Call handler if registered
        eventHandler?.invoke(event)
    }

    /**
     * Get or create a priority queue for a profile.
     */
    private fun getOrCreateQueue(profileId: String): PriorityQueue<CdpEvent> {
        return eventQueues.computeIfAbsent(profileId) {
            PriorityQueue(compareBy { it.ts })
        }
    }

    /**
     * Get or create a dedup cache for a profile.
     */
    private fun getDedupCache(profileId: String): Cache<String, Boolean> {
        return dedupCaches.computeIfAbsent(profileId) {
            Caffeine.newBuilder()
                .expireAfterWrite(dedupTtl.toMillis(), TimeUnit.MILLISECONDS)
                .maximumSize(10000)
                .build()
        }
    }

    /**
     * Get current watermark (for testing).
     */
    fun getCurrentWatermark(): Instant = currentWatermark

    /**
     * Get buffered event count.
     */
    fun getBufferedEventCount(): Long = bufferedEventsGauge.get()

    /**
     * Get processed event count.
     */
    fun getProcessedEventCount(): Long = processedCounter.get()

    /**
     * Get dedup hits count.
     */
    fun getDedupHitsCount(): Long = dedupHitsCounter.get()

    /**
     * Get late events count.
     */
    fun getLateEventsCount(): Long = lateEventsCounter.get()

    /**
     * Get dropped events count.
     */
    fun getDroppedEventsCount(): Long = droppedEventsCounter.get()

    /**
     * Clear all state (for testing).
     */
    fun clear() {
        stop()
        eventQueues.clear()
        dedupCaches.clear()
        bufferedEventsGauge.set(0)
        processedCounter.set(0)
        dedupHitsCounter.set(0)
        lateEventsCounter.set(0)
        droppedEventsCounter.set(0)
        watermarkLagGauge.set(0)
        currentWatermark = clock.instant().minus(processingWindow)
    }
}
