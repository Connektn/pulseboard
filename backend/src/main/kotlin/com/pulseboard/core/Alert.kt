package com.pulseboard.core

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

data class Alert(
    val id: String,
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val ts: Instant,
    val rule: String,
    val entityId: String,
    val severity: Severity,
    val evidence: Map<String, Any?>,
)

enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
}
