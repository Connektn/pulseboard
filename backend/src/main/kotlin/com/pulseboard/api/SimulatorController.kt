package com.pulseboard.api

import com.pulseboard.core.Profile
import com.pulseboard.ingest.Simulator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class SimulatorController(
    @Autowired private val simulator: Simulator,
) {
    private var simulatorScope: CoroutineScope? = null

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
    suspend fun startSimulation(
        @RequestParam(required = false) profile: String?,
        @RequestParam(defaultValue = "10") rps: Int,
        @RequestParam(defaultValue = "90") latenessSec: Long,
    ): Map<String, Any> {
        // Set profile if provided
        if (profile != null) {
            val profileEnum =
                try {
                    Profile.valueOf(profile.uppercase())
                } catch (e: IllegalArgumentException) {
                    return mapOf(
                        "status" to "error",
                        "message" to "Invalid profile: $profile. Valid values: SASE, IGAMING, CDP",
                    )
                }
            simulator.setProfile(profileEnum)
        }

        // Set rate and lateness
        simulator.setRatePerSecond(rps.toDouble())
        simulator.setLatenessSec(latenessSec)

        return if (simulator.isRunning()) {
            mapOf(
                "status" to "already_running",
                "message" to "Simulator is already running",
                "profile" to simulator.getCurrentProfile().name,
                "rps" to simulator.getRatePerSecond(),
                "latenessSec" to simulator.getLatenessSec(),
            )
        } else {
            // Create a new scope for each simulation run
            simulatorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            simulator.start(simulatorScope!!)
            mapOf(
                "status" to "started",
                "message" to "Simulator started successfully",
                "profile" to simulator.getCurrentProfile().name,
                "rps" to simulator.getRatePerSecond(),
                "latenessSec" to simulator.getLatenessSec(),
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
            // Stop the simulator job
            simulator.stop()

            // Cancel and cleanup the scope
            simulatorScope?.cancel()
            simulatorScope = null

            // Small delay to ensure cancellation propagates
            delay(100)

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

    @PostMapping("/sim/config")
    suspend fun updateSimulatorConfig(
        @RequestParam(required = false) rps: Int?,
        @RequestParam(required = false) latenessSec: Long?,
    ): Map<String, Any> {
        // Update RPS if provided
        rps?.let { simulator.setRatePerSecond(it.toDouble()) }

        // Update lateness if provided
        latenessSec?.let { simulator.setLatenessSec(it) }

        return mapOf(
            "status" to "updated",
            "message" to "Simulator configuration updated successfully",
            "rps" to simulator.getRatePerSecond(),
            "latenessSec" to simulator.getLatenessSec(),
        )
    }

    data class ProfileRequest(val profile: Profile)
}
