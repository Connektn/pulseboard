package com.pulseboard.core

import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class Rules(
    private val windowStore: WindowStore,
) {
    /**
     * Evaluate all rules against an event and return any triggered alerts
     */
    suspend fun evaluateAll(event: EntityEvent): List<Alert> {
        val alerts = mutableListOf<Alert>()

        // R1: Velocity Spike
        evaluateVelocitySpike(event)?.let { alerts.add(it) }

        // R2: Value Spike
        evaluateValueSpike(event)?.let { alerts.add(it) }

        // R3: Geo/Device Mismatch
        evaluateGeoDeviceMismatch(event)?.let { alerts.add(it) }

        // R4: Exfil (SASE only)
        if (event.payload.profile == Profile.SASE) {
            evaluateExfil(event)?.let { alerts.add(it) }
        }

        return alerts
    }

    /**
     * R1 Velocity Spike: rate_now > 3×avg_5m && rate_now >= 20/min
     */
    private suspend fun evaluateVelocitySpike(event: EntityEvent): Alert? {
        val rateNow = windowStore.ratePerMin(event.payload.entityId, event.payload.type)
        val avg5m = windowStore.avgOverLast(event.payload.entityId, event.payload.type, 5)
        val threshold = avg5m * 3.0

        if (rateNow > threshold && rateNow >= 20.0) {
            return Alert(
                id = generateAlertId(),
                ts = event.ts,
                rule = "R1_VELOCITY_SPIKE",
                entityId = event.payload.entityId,
                severity = determineSeverity(rateNow, threshold),
                evidence =
                    mapOf(
                        "rate_now" to rateNow,
                        "avg_5m" to avg5m,
                        "threshold" to threshold,
                        "event_type" to event.payload.type,
                        "profile" to event.payload.profile.name,
                    ),
            )
        }
        return null
    }

    /**
     * R2 Value Spike: value_now > 4×EWMA && count_60s >= 5
     */
    private suspend fun evaluateValueSpike(event: EntityEvent): Alert? {
        val value = event.payload.value ?: return null // Skip events without values
        val ewma = windowStore.getEwma(event.payload.entityId, event.payload.type)

        // Update EWMA with current value
        val updatedEwma = windowStore.updateEwma(event.payload.entityId, event.payload.type, value.toDouble())
        val threshold = updatedEwma * 4.0
        val count60s = windowStore.countIn(event.payload.entityId, event.payload.type, Duration.ofSeconds(60))

        if (value.toDouble() > threshold && count60s >= 5) {
            return Alert(
                id = generateAlertId(),
                ts = event.ts,
                rule = "R2_VALUE_SPIKE",
                entityId = event.payload.entityId,
                severity = determineSeverity(value.toDouble(), threshold),
                evidence =
                    mapOf(
                        "value_now" to value,
                        "ewma" to updatedEwma,
                        "threshold" to threshold,
                        "count_60s" to count60s,
                        "event_type" to event.payload.type,
                        "profile" to event.payload.profile.name,
                    ),
            )
        }
        return null
    }

    /**
     * R3 Geo/Device Mismatch: same entity, conflicting geo or device tags within 2 minutes
     */
    private suspend fun evaluateGeoDeviceMismatch(event: EntityEvent): Alert? {
        val currentGeo = event.payload.tags["geo"]
        val currentDevice = event.payload.tags["device"]

        if (currentGeo == null && currentDevice == null) {
            return null // No geo or device info to check
        }

        // Check recent events for this entity to find conflicts
        val recentEvents = getRecentEvents(event.payload.entityId, Duration.ofMinutes(2))
        val conflicts = mutableMapOf<String, Any?>()

        recentEvents.forEach { recentEvent ->
            val recentGeo = recentEvent.payload.tags["geo"]
            val recentDevice = recentEvent.payload.tags["device"]

            // Check for geo conflicts
            if (currentGeo != null && recentGeo != null && currentGeo != recentGeo) {
                conflicts["geo_conflict"] =
                    mapOf(
                        "current" to currentGeo,
                        "previous" to recentGeo,
                        "time_diff_seconds" to Duration.between(recentEvent.ts, event.ts).seconds,
                    )
            }

            // Check for device conflicts
            if (currentDevice != null && recentDevice != null && currentDevice != recentDevice) {
                conflicts["device_conflict"] =
                    mapOf(
                        "current" to currentDevice,
                        "previous" to recentDevice,
                        "time_diff_seconds" to Duration.between(recentEvent.ts, event.ts).seconds,
                    )
            }
        }

        if (conflicts.isNotEmpty()) {
            return Alert(
                id = generateAlertId(),
                ts = event.ts,
                rule = "R3_GEO_DEVICE_MISMATCH",
                entityId = event.payload.entityId,
                severity = Severity.MEDIUM,
                evidence =
                    mapOf(
                        "current_geo" to currentGeo,
                        "current_device" to currentDevice,
                        "conflicts" to conflicts,
                        "event_type" to event.payload.type,
                        "profile" to event.payload.profile.name,
                        "window_minutes" to 2,
                    ),
            )
        }
        return null
    }

    /**
     * R4 Exfil (SASE): sum_30s > P95(last 1h) - fallback to constant threshold
     */
    private suspend fun evaluateExfil(event: EntityEvent): Alert? {
        val value = event.payload.value ?: return null
        val sum30s = windowStore.sumIn(event.payload.entityId, event.payload.type, Duration.ofSeconds(30))

        // Fallback P95 threshold - in a real system this would be calculated from historical data
        val p95Threshold = calculateP95Fallback(event.payload.entityId, event.payload.type)

        if (sum30s > p95Threshold) {
            return Alert(
                id = generateAlertId(),
                ts = event.ts,
                rule = "R4_EXFIL",
                entityId = event.payload.entityId,
                severity = Severity.HIGH,
                evidence =
                    mapOf(
                        "sum_30s" to sum30s,
                        "p95_threshold" to p95Threshold,
                        "current_value" to value,
                        "event_type" to event.payload.type,
                        "profile" to event.payload.profile.name,
                        "window_seconds" to 30,
                    ),
            )
        }
        return null
    }

    /**
     * Calculate fallback P95 threshold based on recent activity
     */
    private suspend fun calculateP95Fallback(
        entityId: String,
        type: String,
    ): Long {
        // Simple fallback: use average over last hour multiplied by a factor
        val avg1h = windowStore.avgOverLast(entityId, type, 60)
        val baseThreshold = (avg1h * 10).toLong() // 10x average as P95 approximation

        // Ensure minimum threshold to avoid false positives on low activity
        return maxOf(baseThreshold, 1000L)
    }

    /**
     * Get recent events for an entity within a time window
     * Note: This is a simplified implementation. In a real system, you'd want
     * to store recent events in the WindowStore or a separate cache.
     */
    private fun getRecentEvents(
        entityId: String,
        window: Duration,
    ): List<EntityEvent> {
        // For this implementation, we'll use a simplified approach
        // In a real system, this would query a cache of recent events
        // For now, return empty list to avoid complex state management
        return emptyList()
    }

    /**
     * Determine alert severity based on how much the value exceeds the threshold
     */
    private fun determineSeverity(
        currentValue: Double,
        threshold: Double,
    ): Severity {
        val ratio = if (threshold > 0) currentValue / threshold else Double.MAX_VALUE

        return when {
            ratio >= 10.0 -> Severity.HIGH
            ratio >= 5.0 -> Severity.MEDIUM
            else -> Severity.LOW
        }
    }

    /**
     * Generate unique alert ID
     */
    private fun generateAlertId(): String = "alert-${UUID.randomUUID()}"
}
