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
        processor = CdpEventProcessor(
            allowedLateness = Duration.ofSeconds(120),
            dedupTtl = Duration.ofMinutes(10)
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
    fun `shuffled sequence should process in timestamp order`() = runBlocking {
        val profileId = "profile-1"
        // Make events old enough to be immediately processed (beyond watermark)
        val baseTime = Instant.now().minusSeconds(200)

        // Create events with timestamps in random order
        val events = listOf(
            createEvent("evt-3", baseTime.plusSeconds(30)),
            createEvent("evt-1", baseTime.plusSeconds(10)),
            createEvent("evt-5", baseTime.plusSeconds(50)),
            createEvent("evt-2", baseTime.plusSeconds(20)),
            createEvent("evt-4", baseTime.plusSeconds(40))
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
    fun `events should be buffered until watermark advances`() = runBlocking {
        val profileId = "profile-1"
        val now = Instant.now()

        // Create event in the future (within allowed lateness)
        val futureEvent = createEvent("evt-future", now.minusSeconds(60))

        processor.submit(futureEvent, profileId)

        // Should be buffered, not processed yet
        assertEquals(1, processor.getBufferedEventCount())
        assertEquals(0, processedEvents.size)

        // Advance watermark
        processor.tick()

        // Should still be buffered (watermark is now - 120s, event is now - 60s)
        assertEquals(1, processor.getBufferedEventCount())
        assertEquals(0, processedEvents.size)

        // Wait for watermark to catch up
        delay(100)
        // Manually set older event
        val oldEvent = createEvent("evt-old", now.minusSeconds(150))
        processor.submit(oldEvent, profileId)
        processor.tick()

        // Old event should be processed
        assertEquals(1, processedEvents.size)
        assertEquals("evt-old", processedEvents[0].eventId)
    }

    @Test
    fun `watermark should drain events older than allowed lateness`() = runBlocking {
        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(200)

        // Create events well before watermark
        val oldEvents = listOf(
            createEvent("evt-1", baseTime),
            createEvent("evt-2", baseTime.plusSeconds(10)),
            createEvent("evt-3", baseTime.plusSeconds(20))
        )

        oldEvents.forEach { processor.submit(it, profileId) }

        // All should be buffered
        assertEquals(3, processor.getBufferedEventCount())

        // Tick to drain
        processor.tick()

        // All should be processed
        assertEquals(0, processor.getBufferedEventCount())
        assertEquals(3, processedEvents.size)
        assertEquals(3, processor.getProcessedEventCount())
    }

    // === Duplicate Detection Tests (DoD Requirement) ===

    @Test
    fun `duplicate eventId should be ignored`() = runBlocking {
        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(150)

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
    fun `multiple duplicates should all be ignored`() = runBlocking {
        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(150)

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
    fun `duplicates across different profiles should be allowed`() = runBlocking {
        val baseTime = Instant.now().minusSeconds(150)

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

    // === Watermark Tests ===

    @Test
    fun `watermark should be computed as now minus allowed lateness`() {
        val before = Instant.now().minus(Duration.ofSeconds(120))
        processor.tick()
        val after = Instant.now().minus(Duration.ofSeconds(120))

        val watermark = processor.getCurrentWatermark()

        assertTrue(watermark >= before)
        assertTrue(watermark <= after)
    }

    @Test
    fun `watermark should advance on each tick`() = runBlocking {
        processor.tick()
        val watermark1 = processor.getCurrentWatermark()

        delay(100)

        processor.tick()
        val watermark2 = processor.getCurrentWatermark()

        assertTrue(watermark2 > watermark1)
    }

    // === Per-Profile Isolation Tests ===

    @Test
    fun `events from different profiles should be processed independently`() = runBlocking {
        val baseTime = Instant.now().minusSeconds(150)

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
    fun `metrics should track buffered and processed events`() = runBlocking {
        val meterRegistry = SimpleMeterRegistry()
        val metricsProcessor = CdpEventProcessor(meterRegistry = meterRegistry)

        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(150)

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
    fun `metrics should track dedup hits`() = runBlocking {
        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(150)

        val event = createEvent("evt-dup", baseTime)

        processor.submit(event, profileId)
        processor.submit(event, profileId)
        processor.submit(event, profileId)

        assertEquals(2, processor.getDedupHitsCount())
    }

    // === Complex Scenario Tests ===

    @Test
    fun `complex scenario with mixed timestamps and duplicates`() = runBlocking {
        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(180)

        // Create a complex sequence with out-of-order and duplicates
        val events = listOf(
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
    fun `ticker should continuously process events when started`() = runBlocking {
        processor.start()

        val profileId = "profile-1"
        val baseTime = Instant.now().minusSeconds(150)

        // Submit events
        processor.submit(createEvent("evt-1", baseTime), profileId)

        // Wait for ticker to process
        delay(1500) // Wait for at least one tick

        // Event should be processed
        assertTrue(processedEvents.isNotEmpty())

        processor.stop()
    }

    // === Helper Functions ===

    private fun createEvent(eventId: String, ts: Instant): CdpEvent {
        return CdpEvent(
            eventId = eventId,
            ts = ts,
            type = CdpEventType.TRACK,
            userId = "user-123",
            name = "test_event"
        )
    }
}
