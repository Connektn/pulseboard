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
            every { mockSimulator.setRatePerSecond(any()) } returns Unit
            every { mockSimulator.setLatenessSec(any()) } returns Unit
            every { mockSimulator.getRatePerSecond() } returns 10.0
            every { mockSimulator.getLatenessSec() } returns 90L
            coEvery { mockSimulator.start(any()) } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.startSimulation(null, 10, 90)

            assertEquals(
                mapOf(
                    "status" to "started",
                    "message" to "Simulator started successfully",
                    "profile" to "SASE",
                    "rps" to 10.0,
                    "latenessSec" to 90L,
                ),
                result,
            )
            coVerify { mockSimulator.start(any()) }
        }

    @Test
    fun `startSimulation should return already running when simulator is active`() =
        runTest {
            every { mockSimulator.isRunning() } returns true
            every { mockSimulator.setRatePerSecond(any()) } returns Unit
            every { mockSimulator.setLatenessSec(any()) } returns Unit
            every { mockSimulator.getRatePerSecond() } returns 10.0
            every { mockSimulator.getLatenessSec() } returns 90L
            every { mockSimulator.getCurrentProfile() } returns Profile.IGAMING

            val result = controller.startSimulation(null, 10, 90)

            assertEquals(
                mapOf(
                    "status" to "already_running",
                    "message" to "Simulator is already running",
                    "profile" to "IGAMING",
                    "rps" to 10.0,
                    "latenessSec" to 90L,
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

    @Test
    fun `startSimulation should accept CDP profile with custom rps and lateness`() =
        runTest {
            every { mockSimulator.isRunning() } returns false
            every { mockSimulator.setProfile(Profile.CDP) } returns Unit
            every { mockSimulator.setRatePerSecond(20.0) } returns Unit
            every { mockSimulator.setLatenessSec(120L) } returns Unit
            every { mockSimulator.getRatePerSecond() } returns 20.0
            every { mockSimulator.getLatenessSec() } returns 120L
            coEvery { mockSimulator.start(any()) } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.CDP

            val result = controller.startSimulation("CDP", 20, 120)

            assertEquals(
                mapOf(
                    "status" to "started",
                    "message" to "Simulator started successfully",
                    "profile" to "CDP",
                    "rps" to 20.0,
                    "latenessSec" to 120L,
                ),
                result,
            )
            verify { mockSimulator.setProfile(Profile.CDP) }
            verify { mockSimulator.setRatePerSecond(20.0) }
            verify { mockSimulator.setLatenessSec(120L) }
        }

    @Test
    fun `startSimulation should reject invalid profile parameter`() =
        runTest {
            val result = controller.startSimulation("INVALID_PROFILE", 10, 90)

            assertEquals("error", result["status"])
            assertEquals("Invalid profile: INVALID_PROFILE. Valid values: SASE, IGAMING, CDP", result["message"])
        }

    @Test
    fun `startSimulation should use default values when not specified`() =
        runTest {
            every { mockSimulator.isRunning() } returns false
            every { mockSimulator.setRatePerSecond(10.0) } returns Unit
            every { mockSimulator.setLatenessSec(90L) } returns Unit
            every { mockSimulator.getRatePerSecond() } returns 10.0
            every { mockSimulator.getLatenessSec() } returns 90L
            coEvery { mockSimulator.start(any()) } returns Unit
            every { mockSimulator.getCurrentProfile() } returns Profile.SASE

            val result = controller.startSimulation(null, 10, 90)

            assertEquals(
                mapOf(
                    "status" to "started",
                    "message" to "Simulator started successfully",
                    "profile" to "SASE",
                    "rps" to 10.0,
                    "latenessSec" to 90L,
                ),
                result,
            )
        }
}
