package com.pulseboard.ingest

import com.pulseboard.cdp.api.CdpEventBus
import com.pulseboard.core.EntityEvent
import com.pulseboard.core.EntityPayload
import com.pulseboard.core.Profile
import com.pulseboard.transport.EventTransport
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
    @Autowired private val eventTransport: EventTransport,
    @Autowired private val cdpEventBus: CdpEventBus,
) {
    private var simulatorJob: Job? = null
    private var currentProfile: Profile = Profile.SASE

    // Configuration
    private var ratePerSecond = 10.0
    private var latenessSec = 90L
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

    // CDP-specific pools
    private val cdpUsers =
        listOf(
            "cdp_user001", "cdp_user002", "cdp_user003", "cdp_user004", "cdp_user005",
            "cdp_user006", "cdp_user007", "cdp_user008", "cdp_user009", "cdp_user010",
            "cdp_user011", "cdp_user012", "cdp_user013", "cdp_user014", "cdp_user015",
            "cdp_user016", "cdp_user017", "cdp_user018", "cdp_user019", "cdp_user020",
            "cdp_user021", "cdp_user022", "cdp_user023", "cdp_user024", "cdp_user025",
            "cdp_user026", "cdp_user027", "cdp_user028", "cdp_user029", "cdp_user030",
        )
    private val cdpAnonymousIds =
        listOf(
            "anon_001", "anon_002", "anon_003", "anon_004", "anon_005",
            "anon_006", "anon_007", "anon_008", "anon_009", "anon_010",
            "anon_011", "anon_012", "anon_013", "anon_014", "anon_015",
        )
    private val cdpCountries = listOf("US", "UK", "DE", "FR", "CA", "AU", "JP")
    private val cdpPlans = listOf("basic", "pro")
    private val cdpTrackEventNames = listOf("Feature Used", "Sign In", "Checkout")
    private val recentEventIds = mutableListOf<String>()

    fun getCurrentProfile(): Profile = currentProfile

    fun setProfile(profile: Profile) {
        currentProfile = profile
    }

    fun setRatePerSecond(rate: Double) {
        ratePerSecond = rate
    }

    fun getRatePerSecond(): Double = ratePerSecond

    fun setLatenessSec(lateness: Long) {
        latenessSec = lateness
    }

    fun getLatenessSec(): Long = latenessSec

    fun start(scope: CoroutineScope) {
        stop() // Stop any existing simulation
        simulatorJob =
            scope.launch {
                while (isActive) {
                    try {
                        when (currentProfile) {
                            Profile.SASE -> generateSaseEvents()
                            Profile.IGAMING -> generateIGamingEvents()
                            Profile.CDP -> generateCdpEvents()
                        }

                        // Add jitter to the rate
                        val jitter = random.nextDouble(0.5, 1.5)
                        val delayMs = ((1000.0 / ratePerSecond) * jitter).toLong()
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
        val eventId = generateEventId(allowDuplicate = true)
        val entityId = entities.random(random)
        val eventType = chooseSaseEventType()
        val timestamp = Instant.now()

        val event =
            when (eventType) {
                "CONN_OPEN" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.SASE,
                                type = "CONN_OPEN",
                                value = random.nextLong(1, 100),
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "device" to devices.random(random),
                                        "protocol" to listOf("tcp", "udp", "http", "https").random(random),
                                    ),
                            ),
                    )

                "CONN_BYTES" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.SASE,
                                type = "CONN_BYTES",
                                value = random.nextLong(100, 50000),
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "direction" to listOf("inbound", "outbound").random(random),
                                    ),
                            ),
                    )

                "LOGIN" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.SASE,
                                type = "LOGIN",
                                value = if (shouldGenerateFailedLogin()) 0L else 1L,
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "device" to devices.random(random),
                                        "browser" to browsers.random(random),
                                        "result" to if (shouldGenerateFailedLogin()) "failed" else "success",
                                    ),
                            ),
                    )

                else -> return
            }

        eventTransport.publishEvent(event)
    }

    private suspend fun generateIGamingEvents() {
        val eventId = generateEventId(allowDuplicate = true)
        val entityId = entities.random(random)
        val eventType = chooseIGamingEventType()
        val timestamp = Instant.now()

        val event =
            when (eventType) {
                "BET_PLACED" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.IGAMING,
                                type = "BET_PLACED",
                                value = random.nextLong(1, 1000),
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "device" to devices.random(random),
                                        "game" to
                                            listOf("slots", "poker", "blackjack", "roulette", "baccarat").random(
                                                random,
                                            ),
                                        "currency" to listOf("USD", "EUR", "GBP", "CAD").random(random),
                                    ),
                            ),
                    )

                "CASHIN" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.IGAMING,
                                type = "CASHIN",
                                value = random.nextLong(1000, 100000),
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "device" to devices.random(random),
                                        "method" to listOf("card", "bank", "crypto", "ewallet").random(random),
                                        "currency" to listOf("USD", "EUR", "GBP", "CAD").random(random),
                                    ),
                            ),
                    )

                "LOGIN" ->
                    EntityEvent(
                        eventId = eventId,
                        ts = timestamp,
                        payload =
                            EntityPayload(
                                entityId = entityId,
                                profile = Profile.IGAMING,
                                type = "LOGIN",
                                value = 1L,
                                tags =
                                    mapOf(
                                        "geo" to geoLocations.random(random),
                                        "device" to devices.random(random),
                                        "browser" to browsers.random(random),
                                        "result" to "success",
                                    ),
                            ),
                    )

                else -> return
            }

        eventTransport.publishEvent(event)
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

    private suspend fun generateCdpEvents() {
        val eventType = chooseCdpEventType()

        when (eventType) {
            "IDENTIFY" -> generateCdpIdentify()
            "TRACK" -> generateCdpTrack()
            "ALIAS" -> generateCdpAlias()
        }
    }

    private suspend fun generateCdpIdentify() {
        val userId = cdpUsers.random(random)
        val event =
            com.pulseboard.cdp.model.CdpEvent(
                eventId = generateEventId(allowDuplicate = true),
                ts = applyJitter(Instant.now()),
                type = com.pulseboard.cdp.model.CdpEventType.IDENTIFY,
                userId = userId,
                email = "$userId@example.com",
                anonymousId = null,
                name = null,
                properties = emptyMap(),
                traits =
                    mapOf(
                        "plan" to cdpPlans.random(random),
                        "country" to cdpCountries.random(random),
                    ),
            )
        cdpEventBus.publish(event)
    }

    private suspend fun generateCdpTrack() {
        val userId = cdpUsers.random(random)
        val event =
            com.pulseboard.cdp.model.CdpEvent(
                eventId = generateEventId(allowDuplicate = true),
                ts = applyJitter(Instant.now()),
                type = com.pulseboard.cdp.model.CdpEventType.TRACK,
                userId = userId,
                email = null,
                anonymousId = null,
                name = cdpTrackEventNames.random(random),
                properties = mapOf("source" to "simulator"),
                traits = emptyMap(),
            )
        cdpEventBus.publish(event)
    }

    private suspend fun generateCdpAlias() {
        // Create realistic ALIAS events: each anonymous ID maps to at most one user ID
        // Use a deterministic mapping: anon_XXX -> cdp_userXXX (if it exists)
        val anonId = cdpAnonymousIds.random(random)

        // Extract number from anon_XXX
        val anonNumber = anonId.substringAfter("anon_")

        // Map to user with same number (if it exists in cdpUsers list)
        // anon_001 -> cdp_user001, anon_007 -> cdp_user007, etc.
        val userId = "cdp_user$anonNumber"

        // Only create ALIAS if this user exists in our pool
        if (!cdpUsers.contains(userId)) {
            // If no matching user, skip this ALIAS
            return
        }

        // No duplicates for ALIAS
        val event =
            com.pulseboard.cdp.model.CdpEvent(
                eventId = generateEventId(allowDuplicate = false),
                ts = applyJitter(Instant.now()),
                type = com.pulseboard.cdp.model.CdpEventType.ALIAS,
                userId = userId,
                email = null,
                anonymousId = anonId,
                name = null,
                properties = emptyMap(),
                traits = emptyMap(),
            )
        cdpEventBus.publish(event)
    }

    private fun chooseCdpEventType(): String {
        val rand = random.nextDouble()
        return when {
            rand < 0.20 -> "IDENTIFY" // 20%
            rand < 0.90 -> "TRACK" // 70%
            else -> "ALIAS" // 10%
        }
    }

    private fun applyJitter(instant: Instant): Instant {
        val jitterSec = random.nextLong(-latenessSec, latenessSec + 1)
        return instant.plusSeconds(jitterSec)
    }

    private fun generateEventId(allowDuplicate: Boolean): String {
        // 5% chance of duplicate if allowed
        if (allowDuplicate && random.nextDouble() < 0.05 && recentEventIds.isNotEmpty()) {
            return recentEventIds.random(random)
        }

        val eventId = "evt-${System.nanoTime()}-${random.nextInt(10000)}"
        recentEventIds.add(eventId)

        // Keep only last 100 event IDs for duplicate sampling
        if (recentEventIds.size > 100) {
            recentEventIds.removeAt(0)
        }

        return eventId
    }
}
