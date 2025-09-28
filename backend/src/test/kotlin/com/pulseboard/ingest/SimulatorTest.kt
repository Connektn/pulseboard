package com.pulseboard.ingest

import com.pulseboard.core.Profile
import com.pulseboard.transport.EventTransport
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulatorTest {
    private lateinit var mockEventTransport: EventTransport
    private lateinit var simulator: Simulator

    @BeforeEach
    fun setup() {
        mockEventTransport = mockk()
        coEvery { mockEventTransport.publishEvent(any()) } returns Unit
        simulator = Simulator(mockEventTransport)
    }

    @Test
    fun `should have default SASE profile`() {
        assertEquals(Profile.SASE, simulator.getCurrentProfile())
    }

    @Test
    fun `should set and get profile correctly`() {
        simulator.setProfile(Profile.IGAMING)
        assertEquals(Profile.IGAMING, simulator.getCurrentProfile())

        simulator.setProfile(Profile.SASE)
        assertEquals(Profile.SASE, simulator.getCurrentProfile())
    }

    @Test
    fun `should not be running initially`() {
        assertFalse(simulator.isRunning())
    }

    @Test
    fun `should start and stop simulator`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())

            // Initially not running
            assertFalse(simulator.isRunning())

            // Start simulator
            simulator.start(scope)
            assertTrue(simulator.isRunning())

            // Give it a moment to generate some events
            delay(100)

            // Stop simulator
            simulator.stop()
            assertFalse(simulator.isRunning())
        }

    @Test
    fun `should generate SASE events when running with SASE profile`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())
            simulator.setProfile(Profile.SASE)

            simulator.start(scope)
            delay(200) // Let it run for a bit
            simulator.stop()

            // Verify that events were published
            coVerify(atLeast = 1) { mockEventTransport.publishEvent(any()) }
        }

    @Test
    fun `should generate iGaming events when running with iGaming profile`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())
            simulator.setProfile(Profile.IGAMING)

            simulator.start(scope)
            delay(200) // Let it run for a bit
            simulator.stop()

            // Verify that events were published
            coVerify(atLeast = 1) { mockEventTransport.publishEvent(any()) }
        }

    @Test
    fun `should stop previous simulation when starting new one`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())

            // Start first simulation
            simulator.start(scope)
            assertTrue(simulator.isRunning())

            delay(50)

            // Start second simulation (should stop first)
            simulator.start(scope)
            assertTrue(simulator.isRunning())

            delay(50)
            simulator.stop()
            assertFalse(simulator.isRunning())
        }

    @Test
    fun `should handle multiple stop calls gracefully`() =
        runTest {
            val scope = CoroutineScope(SupervisorJob())

            simulator.start(scope)
            assertTrue(simulator.isRunning())

            simulator.stop()
            assertFalse(simulator.isRunning())

            // Stop again - should not throw exception
            simulator.stop()
            assertFalse(simulator.isRunning())
        }
}
