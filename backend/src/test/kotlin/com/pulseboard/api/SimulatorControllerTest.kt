package com.pulseboard.api

import com.pulseboard.core.Profile
import com.pulseboard.ingest.Simulator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SimulatorControllerTest {
    private lateinit var mockSimulator: Simulator
    private lateinit var controller: SimulatorController

    @BeforeEach
    fun setup() {
        mockSimulator = mockk()
        controller = SimulatorController(mockSimulator)
    }

    @Test
    fun `getProfile should return current profile`() =
        runTest {
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.getProfile()

            assertEquals(mapOf("profile" to "SASE"), result)
            verify { mockSimulator.getCurrentProfile() }
        }

    @Test
    fun `setProfile should update profile and return success message`() =
        runTest {
            every { mockSimulator.setProfile(Profile.IGAMING) } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.IGAMING

            val request = SimulatorController.ProfileRequest(Profile.IGAMING)
            val result = controller.setProfile(request)

            assertEquals(
                mapOf(
                    "profile" to "IGAMING",
                    "message" to "Profile updated successfully",
                ),
                result,
            )
            verify { mockSimulator.setProfile(Profile.IGAMING) }
        }

    @Test
    fun `startSimulation should start simulator when not running`() =
        runTest {
            every { mockSimulator.isRunning() } returns false
            coEvery { mockSimulator.start(any()) } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.startSimulation()

            assertEquals(
                mapOf(
                    "status" to "started",
                    "message" to "Simulator started successfully",
                    "profile" to "SASE",
                ),
                result,
            )
            coVerify { mockSimulator.start(any()) }
        }

    @Test
    fun `startSimulation should return already running when simulator is active`() =
        runTest {
            every { mockSimulator.isRunning() } returns true
            every { mockSimulator.getCurrentProfile() } returns Profile.IGAMING

            val result = controller.startSimulation()

            assertEquals(
                mapOf(
                    "status" to "already_running",
                    "message" to "Simulator is already running",
                    "profile" to "IGAMING",
                ),
                result,
            )
            coVerify(exactly = 0) { mockSimulator.start(any()) }
        }

    @Test
    fun `stopSimulation should stop simulator when running`() =
        runTest {
            every { mockSimulator.isRunning() } returns true
            every { mockSimulator.stop() } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.stopSimulation()

            assertEquals(
                mapOf(
                    "status" to "stopped",
                    "message" to "Simulator stopped successfully",
                    "profile" to "SASE",
                ),
                result,
            )
            verify { mockSimulator.stop() }
        }

    @Test
    fun `stopSimulation should return already stopped when simulator is not running`() =
        runTest {
            every { mockSimulator.isRunning() } returns false
            every { mockSimulator.getCurrentProfile() } returns Profile.IGAMING

            val result = controller.stopSimulation()

            assertEquals(
                mapOf(
                    "status" to "already_stopped",
                    "message" to "Simulator is not running",
                    "profile" to "IGAMING",
                ),
                result,
            )
            verify(exactly = 0) { mockSimulator.stop() }
        }

    @Test
    fun `getSimulatorStatus should return current status and profile`() =
        runTest {
            every { mockSimulator.isRunning() } returns true
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.getSimulatorStatus()

            assertEquals(
                mapOf(
                    "running" to true,
                    "profile" to "SASE",
                    "status" to "running",
                ),
                result,
            )
        }

    @Test
    fun `getSimulatorStatus should return stopped status when not running`() =
        runTest {
            every { mockSimulator.isRunning() } returns false
            every { mockSimulator.getCurrentProfile() } returns Profile.IGAMING

            val result = controller.getSimulatorStatus()

            assertEquals(
                mapOf(
                    "running" to false,
                    "profile" to "IGAMING",
                    "status" to "stopped",
                ),
                result,
            )
        }
}
