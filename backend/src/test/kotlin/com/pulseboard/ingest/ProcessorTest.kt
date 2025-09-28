package com.pulseboard.ingest

import com.pulseboard.core.Event
import com.pulseboard.core.Profile
import com.pulseboard.core.Rules
import com.pulseboard.core.WindowStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessorTest {
    private lateinit var mockEventBus: EventBus
    private lateinit var mockWindowStore: WindowStore
    private lateinit var mockRules: Rules
    private lateinit var processor: Processor
    private lateinit var eventFlow: MutableSharedFlow<Event>
    private val testTimestamp = Instant.parse("2023-12-01T12:00:00Z")

    @BeforeEach
    fun setup() {
        mockEventBus = mockk()
        mockWindowStore = mockk()
        mockRules = mockk()
        eventFlow = MutableSharedFlow()
        every { mockEventBus.events } returns eventFlow
        processor = Processor(mockEventBus, mockWindowStore, mockRules)
    }

    @Test
    fun `should not be running initially`() {
        assertFalse(processor.isRunning())
    }

    @Test
    fun `should start and stop processing`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())

            // Initially not running
            assertFalse(processor.isRunning())

            // Start processor
            processor.startProcessing(scope)
            assertTrue(processor.isRunning())

            // Stop processor
            processor.stopProcessing()
            assertFalse(processor.isRunning())
        }

    @Test
    fun `should restart processing when called multiple times`() =
        runTest {
            val scope1 = CoroutineScope(SupervisorJob())
            val scope2 = CoroutineScope(SupervisorJob())

            // Start first time
            processor.startProcessing(scope1)
            assertTrue(processor.isRunning())

            // Start second time (should stop first and start new)
            processor.startProcessing(scope2)
            assertTrue(processor.isRunning())

            processor.stopProcessing()
            assertFalse(processor.isRunning())
        }

    @Test
    fun `should return correct processing statistics`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())

            // Initially stopped
            var stats = processor.getProcessingStats()
            assertEquals(false, stats["isRunning"])

            // Start processing
            processor.startProcessing(scope)
            stats = processor.getProcessingStats()
            assertEquals(true, stats["isRunning"])
            assertEquals(true, stats["processingJobActive"])
            assertEquals(false, stats["processingJobCompleted"])
            assertEquals(false, stats["processingJobCancelled"])

            // Stop processing
            processor.stopProcessing()
            stats = processor.getProcessingStats()
            assertEquals(false, stats["isRunning"])
        }

    private fun createTestEvent(
        type: String,
        entityId: String,
        value: Long?,
        profile: Profile = Profile.SASE,
        tags: Map<String, String> = emptyMap(),
    ): Event {
        return Event(
            ts = testTimestamp,
            profile = profile,
            type = type,
            entityId = entityId,
            value = value,
            tags = tags,
        )
    }
}
