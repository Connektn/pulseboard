package com.pulseboard.core

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

data class Event(
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    val ts: Instant,
    val profile: Profile,
    val type: String,
    val entityId: String,
    val value: Long? = null,
    val tags: Map<String, String> = emptyMap()
)