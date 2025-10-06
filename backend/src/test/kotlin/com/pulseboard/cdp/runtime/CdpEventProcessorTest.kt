package com.pulseboard.cdp.runtime

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.cdp.model.CdpEventType
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CdpEventProcessorTest {
    private lateinit var processor: CdpEventProcessor
    private val processedEvents = mutableListOf<CdpEvent>()

    @BeforeEach
    fun setup() {
        processor =
            CdpEventProcessor(
                processingWindow = Duration.ofSeconds(5),
                lateEventGracePeriod = Duration.ofSeconds(120),
                dedupTtl = Duration.ofMinutes(10),
            )
        processedEvents.clear()

        // Set up event handler to capture processed events
        processor.onEvent { event ->
            processedEvents.add(event)
        }
    }

    @AfterEach
    fun teardown() {
        processor.stop()
        processor.clear()
    }

    // === Out-of-Order Processing Tests (DoD Requirement) ===

    @Test
    fun `shuffled sequence should process in timestamp order`() =
        runBlocking {
            val profileId = "profile-1"
            // Make events old enough to be immediately processed (beyond processing window)
            // but within grace period (120s)
            val baseTime = Instant.now().minusSeconds(60)

            // Create events with timestamps in random order
            val events =
                listOf(
                    createEvent("evt-3", baseTime.plusSeconds(30)),
                    createEvent("evt-1", baseTime.plusSeconds(10)),
                    createEvent("evt-5", baseTime.plusSeconds(50)),
                    createEvent("evt-2", baseTime.plusSeconds(20)),
                    createEvent("evt-4", baseTime.plusSeconds(40)),
                )

            // Submit in shuffled order
            events.forEach { processor.submit(it, profileId) }

            // Run ticker to process events
            processor.tick()

            // Verify events processed in timestamp order
            assertEquals(5, processedEvents.size)
            assertEquals("evt-1", processedEvents[0].eventId)
            assertEquals("evt-2", processedEvents[1].eventId)
            assertEquals("evt-3", processedEvents[2].eventId)
            assertEquals("evt-4", processedEvents[3].eventId)
            assertEquals("evt-5", processedEvents[4].eventId)

            // Verify timestamps are in ascending order
            for (i in 1 until processedEvents.size) {
                assertTrue(processedEvents[i - 1].ts <= processedEvents[i].ts)
            }
        }

    @Test
    fun `events should be buffered until watermark advances`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Create event just within processing window (3 seconds ago)
            val recentEvent = createEvent("evt-recent", now.minusSeconds(3))

            processor.submit(recentEvent, profileId)

            // Should be buffered, not processed yet
            assertEquals(1, processor.getBufferedEventCount())
            assertEquals(0, processedEvents.size)

            // Advance watermark (watermark is now - 5s, event is now - 3s)
            processor.tick()

            // Should still be buffered
            assertEquals(1, processor.getBufferedEventCount())
            assertEquals(0, processedEvents.size)

            // Submit older event that's beyond processing window
            val oldEvent = createEvent("evt-old", now.minusSeconds(10))
            processor.submit(oldEvent, profileId)
            processor.tick()

            // Old event should be processed
            assertEquals(1, processedEvents.size)
            assertEquals("evt-old", processedEvents[0].eventId)
        }

    @Test
    fun `watermark should drain events older than processing window`() =
        runBlocking {
            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(20)

            // Create events beyond processing window (5s) but within grace period
            val oldEvents =
                listOf(
                    createEvent("evt-1", baseTime),
                    createEvent("evt-2", baseTime.plusSeconds(5)),
                    createEvent("evt-3", baseTime.plusSeconds(10)),
                )

            oldEvents.forEach { processor.submit(it, profileId) }

            // All should be buffered
            assertEquals(3, processor.getBufferedEventCount())

            // Tick to drain
            processor.tick()

            // All should be processed (beyond 5s processing window)
            assertEquals(0, processor.getBufferedEventCount())
            assertEquals(3, processedEvents.size)
            assertEquals(3, processor.getProcessedEventCount())
        }

    // === Duplicate Detection Tests (DoD Requirement) ===

    @Test
    fun `duplicate eventId should be ignored`() =
        runBlocking {
            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            val event1 = createEvent("evt-duplicate", baseTime)
            val event2 = createEvent("evt-duplicate", baseTime.plusSeconds(10)) // Same ID, different timestamp

            processor.submit(event1, profileId)
            processor.submit(event2, profileId) // Should be ignored

            processor.tick()

            // Only first event should be processed
            assertEquals(1, processedEvents.size)
            assertEquals("evt-duplicate", processedEvents[0].eventId)
            assertEquals(1, processor.getDedupHitsCount())
        }

    @Test
    fun `multiple duplicates should all be ignored`() =
        runBlocking {
            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            val event = createEvent("evt-dup", baseTime)

            // Submit same event 5 times
            repeat(5) {
                processor.submit(event, profileId)
            }

            processor.tick()

            // Only one should be processed
            assertEquals(1, processedEvents.size)
            assertEquals(4, processor.getDedupHitsCount())
        }

    @Test
    fun `duplicates across different profiles should be allowed`() =
        runBlocking {
            val baseTime = Instant.now().minusSeconds(60)

            val event = createEvent("evt-shared", baseTime)

            // Submit to different profiles
            processor.submit(event, "profile-1")
            processor.submit(event, "profile-2")
            processor.submit(event, "profile-3")

            processor.tick()

            // All should be processed (different profiles)
            assertEquals(3, processedEvents.size)
            assertEquals(0, processor.getDedupHitsCount())
        }

    // === Late Event and Dropped Event Tests ===

    @Test
    fun `events within processing window should be accepted without warning`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Event 3 seconds ago (within 5s processing window)
            val recentEvent = createEvent("evt-recent", now.minusSeconds(3))
            processor.submit(recentEvent, profileId)

            assertEquals(1, processor.getBufferedEventCount())
            assertEquals(0, processor.getLateEventsCount())
            assertEquals(0, processor.getDroppedEventsCount())
        }

    @Test
    fun `events beyond processing window but within grace period should be marked as late`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Event 30 seconds ago (beyond 5s processing window, within 120s grace period)
            val lateEvent = createEvent("evt-late", now.minusSeconds(30))
            processor.submit(lateEvent, profileId)

            assertEquals(1, processor.getBufferedEventCount())
            assertEquals(1, processor.getLateEventsCount())
            assertEquals(0, processor.getDroppedEventsCount())
        }

    @Test
    fun `events beyond grace period should be dropped`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Event 150 seconds ago (beyond 120s grace period)
            val tooLateEvent = createEvent("evt-too-late", now.minusSeconds(150))
            processor.submit(tooLateEvent, profileId)

            // Should not be buffered
            assertEquals(0, processor.getBufferedEventCount())
            assertEquals(0, processor.getLateEventsCount())
            assertEquals(1, processor.getDroppedEventsCount())

            // Should not be processed
            processor.tick()
            assertEquals(0, processedEvents.size)
        }

    @Test
    fun `multiple late and dropped events should be tracked correctly`() =
        runBlocking {
            val profileId = "profile-1"
            val now = Instant.now()

            // Normal event (within processing window)
            processor.submit(createEvent("evt-normal", now.minusSeconds(2)), profileId)

            // Late events (beyond processing window, within grace period)
            processor.submit(createEvent("evt-late-1", now.minusSeconds(20)), profileId)
            processor.submit(createEvent("evt-late-2", now.minusSeconds(50)), profileId)

            // Dropped events (beyond grace period)
            processor.submit(createEvent("evt-dropped-1", now.minusSeconds(130)), profileId)
            processor.submit(createEvent("evt-dropped-2", now.minusSeconds(200)), profileId)

            // Verify counts
            assertEquals(3, processor.getBufferedEventCount()) // 1 normal + 2 late
            assertEquals(2, processor.getLateEventsCount())
            assertEquals(2, processor.getDroppedEventsCount())
        }

    // === Watermark Tests ===

    @Test
    fun `watermark should be computed as now minus processing window`() {
        val before = Instant.now().minus(Duration.ofSeconds(5))
        processor.tick()
        val after = Instant.now().minus(Duration.ofSeconds(5))

        val watermark = processor.getCurrentWatermark()

        assertTrue(watermark >= before)
        assertTrue(watermark <= after)
    }

    @Test
    fun `watermark should advance on each tick`() =
        runBlocking {
            processor.tick()
            val watermark1 = processor.getCurrentWatermark()

            delay(100)

            processor.tick()
            val watermark2 = processor.getCurrentWatermark()

            assertTrue(watermark2 > watermark1)
        }

    // === Per-Profile Isolation Tests ===

    @Test
    fun `events from different profiles should be processed independently`() =
        runBlocking {
            val baseTime = Instant.now().minusSeconds(60)

            // Submit events for different profiles
            processor.submit(createEvent("evt-p1-1", baseTime), "profile-1")
            processor.submit(createEvent("evt-p2-1", baseTime.plusSeconds(5)), "profile-2")
            processor.submit(createEvent("evt-p1-2", baseTime.plusSeconds(10)), "profile-1")
            processor.submit(createEvent("evt-p2-2", baseTime.plusSeconds(15)), "profile-2")

            processor.tick()

            // All should be processed
            assertEquals(4, processedEvents.size)

            // Each profile's events should be in order
            val p1Events = processedEvents.filter { it.eventId.contains("p1") }
            val p2Events = processedEvents.filter { it.eventId.contains("p2") }

            assertEquals(2, p1Events.size)
            assertEquals(2, p2Events.size)

            assertTrue(p1Events[0].ts < p1Events[1].ts)
            assertTrue(p2Events[0].ts < p2Events[1].ts)
        }

    // === Metrics Tests ===

    @Test
    fun `metrics should track buffered and processed events`() =
        runBlocking {
            val meterRegistry = SimpleMeterRegistry()
            val metricsProcessor = CdpEventProcessor(meterRegistry = meterRegistry)

            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            // Submit events
            metricsProcessor.submit(createEvent("evt-1", baseTime), profileId)
            metricsProcessor.submit(createEvent("evt-2", baseTime.plusSeconds(10)), profileId)

            assertEquals(2, metricsProcessor.getBufferedEventCount())
            assertEquals(0, metricsProcessor.getProcessedEventCount())

            // Process
            metricsProcessor.tick()

            assertEquals(0, metricsProcessor.getBufferedEventCount())
            assertEquals(2, metricsProcessor.getProcessedEventCount())

            metricsProcessor.clear()
        }

    @Test
    fun `metrics should track dedup hits`() =
        runBlocking {
            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            val event = createEvent("evt-dup", baseTime)

            processor.submit(event, profileId)
            processor.submit(event, profileId)
            processor.submit(event, profileId)

            assertEquals(2, processor.getDedupHitsCount())
        }

    // === Complex Scenario Tests ===

    @Test
    fun `complex scenario with mixed timestamps and duplicates`() =
        runBlocking {
            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            // Create a complex sequence with out-of-order and duplicates
            val events =
                listOf(
                    createEvent("evt-1", baseTime.plusSeconds(10)),
                    createEvent("evt-3", baseTime.plusSeconds(30)),
                    createEvent("evt-1", baseTime.plusSeconds(10)), // Duplicate
                    createEvent("evt-2", baseTime.plusSeconds(20)),
                    createEvent("evt-5", baseTime.plusSeconds(50)),
                    createEvent("evt-4", baseTime.plusSeconds(40)),
                    createEvent("evt-3", baseTime.plusSeconds(30)), // Duplicate
                )

            events.forEach { processor.submit(it, profileId) }

            processor.tick()

            // Should have 5 unique events processed in order
            assertEquals(5, processedEvents.size)
            assertEquals(2, processor.getDedupHitsCount())

            // Verify order
            assertEquals("evt-1", processedEvents[0].eventId)
            assertEquals("evt-2", processedEvents[1].eventId)
            assertEquals("evt-3", processedEvents[2].eventId)
            assertEquals("evt-4", processedEvents[3].eventId)
            assertEquals("evt-5", processedEvents[4].eventId)
        }

    @Test
    fun `ticker should continuously process events when started`() =
        runBlocking {
            processor.start()

            val profileId = "profile-1"
            val baseTime = Instant.now().minusSeconds(60)

            // Submit events
            processor.submit(createEvent("evt-1", baseTime), profileId)

            // Wait for ticker to process
            delay(6000) // Wait for at least one tick (5s processing window + 1s ticker)

            // Event should be processed
            assertTrue(processedEvents.isNotEmpty())

            processor.stop()
        }

    // === Helper Functions ===

    private fun createEvent(
        eventId: String,
        ts: Instant,
    ): CdpEvent {
        return CdpEvent(
            eventId = eventId,
            ts = ts,
            type = CdpEventType.TRACK,
            userId = "user-123",
            name = "test_event",
        )
    }
}
