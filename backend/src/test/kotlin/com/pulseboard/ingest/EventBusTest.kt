package com.pulseboard.ingest

import com.pulseboard.core.Alert
import com.pulseboard.core.Event
import com.pulseboard.core.Profile
import com.pulseboard.core.Severity
import kotlinx.coroutines.flow.SharedFlow
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventBusTest {
    @Test
    fun `EventBus should have events and alerts SharedFlows`() {
        val eventBus = EventBus()

        assertNotNull(eventBus.events)
        assertNotNull(eventBus.alerts)
        assertTrue(eventBus.events is SharedFlow<Event>)
        assertTrue(eventBus.alerts is SharedFlow<Alert>)
    }

    @Test
    fun `tryPublishEvent should return true when successful`() {
        val eventBus = EventBus()
        val testEvent =
            Event(
                ts = Instant.parse("2023-12-01T10:30:00Z"),
                profile = Profile.SASE,
                type = "LOGIN",
                entityId = "user999",
            )

        val result = eventBus.tryPublishEvent(testEvent)
        assertTrue(result)
    }

    @Test
    fun `tryPublishAlert should return true when successful`() {
        val eventBus = EventBus()
        val testAlert =
            Alert(
                id = "alert-999",
                ts = Instant.parse("2023-12-01T12:00:00Z"),
                rule = "R3_GEO_MISMATCH",
                entityId = "user111",
                severity = Severity.LOW,
                evidence = mapOf("geo1" to "US", "geo2" to "CN"),
            )

        val result = eventBus.tryPublishAlert(testAlert)
        assertTrue(result)
    }
}
