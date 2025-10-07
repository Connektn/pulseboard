package com.pulseboard.ingest

import com.pulseboard.core.Event

interface EventBus<E : Event> {
    suspend fun publishEvent(event: E)

    fun tryPublishEvent(event: E): Boolean
}
