package com.pulseboard.ingest

import com.pulseboard.core.Event
import com.pulseboard.core.Profile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.random.Random

@Component
class Simulator(
    @Autowired private val eventBus: EventBus,
) {
    private var simulatorJob: Job? = null
    private var currentProfile: Profile = Profile.SASE

    // Configuration
    private val baseRatePerSecond = 10.0
    private val burstFactor = 2.0
    private val random = Random(System.currentTimeMillis())

    // Entity pools for realistic simulation
    private val entities =
        listOf(
            "user001", "user002", "user003", "user004", "user005",
            "user006", "user007", "user008", "user009", "user010",
            "user011", "user012", "user013", "user014", "user015",
        )

    private val geoLocations = listOf("US", "UK", "DE", "FR", "CA", "AU", "JP", "CN", "IN", "BR")
    private val devices = listOf("desktop", "mobile", "tablet")
    private val browsers = listOf("chrome", "firefox", "safari", "edge")

    fun getCurrentProfile(): Profile = currentProfile

    fun setProfile(profile: Profile) {
        currentProfile = profile
    }

    fun start(scope: CoroutineScope) {
        stop() // Stop any existing simulation
        simulatorJob =
            scope.launch {
                while (isActive) {
                    try {
                        when (currentProfile) {
                            Profile.SASE -> generateSaseEvents()
                            Profile.IGAMING -> generateIGamingEvents()
                        }

                        // Add jitter to the rate
                        val jitter = random.nextDouble(0.5, 1.5)
                        val delayMs = ((1000.0 / baseRatePerSecond) * jitter).toLong()
                        delay(delayMs)
                    } catch (e: Exception) {
                        // Log error but continue simulation
                        println("Simulation error: ${e.message}")
                    }
                }
            }
    }

    fun stop() {
        simulatorJob?.cancel()
        simulatorJob = null
    }

    fun isRunning(): Boolean = simulatorJob?.isActive == true

    private suspend fun generateSaseEvents() {
        val entityId = entities.random(random)
        val eventType = chooseSaseEventType()
        val timestamp = Instant.now()

        val event =
            when (eventType) {
                "CONN_OPEN" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.SASE,
                        type = "CONN_OPEN",
                        entityId = entityId,
                        value = random.nextLong(1, 100),
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "device" to devices.random(random),
                                "protocol" to listOf("tcp", "udp", "http", "https").random(random),
                            ),
                    )
                "CONN_BYTES" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.SASE,
                        type = "CONN_BYTES",
                        entityId = entityId,
                        value = random.nextLong(100, 50000),
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "direction" to listOf("inbound", "outbound").random(random),
                            ),
                    )
                "LOGIN" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.SASE,
                        type = "LOGIN",
                        entityId = entityId,
                        value = if (shouldGenerateFailedLogin()) 0L else 1L,
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "device" to devices.random(random),
                                "browser" to browsers.random(random),
                                "result" to if (shouldGenerateFailedLogin()) "failed" else "success",
                            ),
                    )
                else -> return
            }

        eventBus.publishEvent(event)
    }

    private suspend fun generateIGamingEvents() {
        val entityId = entities.random(random)
        val eventType = chooseIGamingEventType()
        val timestamp = Instant.now()

        val event =
            when (eventType) {
                "BET_PLACED" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.IGAMING,
                        type = "BET_PLACED",
                        entityId = entityId,
                        value = random.nextLong(1, 1000),
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "device" to devices.random(random),
                                "game" to listOf("slots", "poker", "blackjack", "roulette", "baccarat").random(random),
                                "currency" to listOf("USD", "EUR", "GBP", "CAD").random(random),
                            ),
                    )
                "CASHIN" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.IGAMING,
                        type = "CASHIN",
                        entityId = entityId,
                        value = random.nextLong(1000, 100000),
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "device" to devices.random(random),
                                "method" to listOf("card", "bank", "crypto", "ewallet").random(random),
                                "currency" to listOf("USD", "EUR", "GBP", "CAD").random(random),
                            ),
                    )
                "LOGIN" ->
                    Event(
                        ts = timestamp,
                        profile = Profile.IGAMING,
                        type = "LOGIN",
                        entityId = entityId,
                        value = 1L,
                        tags =
                            mapOf(
                                "geo" to geoLocations.random(random),
                                "device" to devices.random(random),
                                "browser" to browsers.random(random),
                                "result" to "success",
                            ),
                    )
                else -> return
            }

        eventBus.publishEvent(event)
    }

    private fun chooseSaseEventType(): String {
        val rand = random.nextDouble()
        return when {
            rand < 0.5 -> "CONN_OPEN"
            rand < 0.8 -> "CONN_BYTES"
            else -> "LOGIN"
        }
    }

    private fun chooseIGamingEventType(): String {
        val rand = random.nextDouble()
        return when {
            rand < 0.6 -> "BET_PLACED"
            rand < 0.8 -> "CASHIN"
            else -> "LOGIN"
        }
    }

    private fun shouldGenerateFailedLogin(): Boolean {
        // 10% chance of failed login for SASE (more security focused)
        return random.nextDouble() < 0.1
    }
}
