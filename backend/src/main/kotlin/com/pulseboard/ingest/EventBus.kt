package com.pulseboard.ingest

import com.pulseboard.core.Alert
import com.pulseboard.core.Event
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.springframework.stereotype.Component

@Component
class EventBus {
    private val _events =
        MutableSharedFlow<Event>(
            replay = 0,
            extraBufferCapacity = 1000,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private val _alerts =
        MutableSharedFlow<Alert>(
            replay = 0,
            extraBufferCapacity = 1000,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events: SharedFlow<Event> = _events.asSharedFlow()
    val alerts: SharedFlow<Alert> = _alerts.asSharedFlow()

    suspend fun publishEvent(event: Event) {
        _events.emit(event)
    }

    suspend fun publishAlert(alert: Alert) {
        _alerts.emit(alert)
    }

    fun tryPublishEvent(event: Event): Boolean {
        return _events.tryEmit(event)
    }

    fun tryPublishAlert(alert: Alert): Boolean {
        return _alerts.tryEmit(alert)
    }
}
