package com.pulseboard.api

import com.pulseboard.core.Profile
import com.pulseboard.ingest.Simulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SimulatorController(
    @Autowired private val simulator: Simulator,
) {
    private val simulatorScope = CoroutineScope(SupervisorJob())

    @GetMapping("/profile")
    suspend fun getProfile(): Map<String, String> {
        return mapOf("profile" to simulator.getCurrentProfile().name)
    }

    @PostMapping("/profile")
    suspend fun setProfile(
        @RequestBody request: ProfileRequest,
    ): Map<String, String> {
        simulator.setProfile(request.profile)
        return mapOf(
            "profile" to simulator.getCurrentProfile().name,
            "message" to "Profile updated successfully",
        )
    }

    @PostMapping("/sim/start")
    suspend fun startSimulation(): Map<String, Any> {
        return if (simulator.isRunning()) {
            mapOf(
                "status" to "already_running",
                "message" to "Simulator is already running",
                "profile" to simulator.getCurrentProfile().name,
            )
        } else {
            simulator.start(simulatorScope)
            mapOf(
                "status" to "started",
                "message" to "Simulator started successfully",
                "profile" to simulator.getCurrentProfile().name,
            )
        }
    }

    @PostMapping("/sim/stop")
    suspend fun stopSimulation(): Map<String, Any> {
        return if (!simulator.isRunning()) {
            mapOf(
                "status" to "already_stopped",
                "message" to "Simulator is not running",
                "profile" to simulator.getCurrentProfile().name,
            )
        } else {
            simulator.stop()
            mapOf(
                "status" to "stopped",
                "message" to "Simulator stopped successfully",
                "profile" to simulator.getCurrentProfile().name,
            )
        }
    }

    @GetMapping("/sim/status")
    suspend fun getSimulatorStatus(): Map<String, Any> {
        return mapOf(
            "running" to simulator.isRunning(),
            "profile" to simulator.getCurrentProfile().name,
            "status" to if (simulator.isRunning()) "running" else "stopped",
        )
    }

    data class ProfileRequest(val profile: Profile)
}
