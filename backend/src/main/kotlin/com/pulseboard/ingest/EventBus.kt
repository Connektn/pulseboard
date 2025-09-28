package com.pulseboard.ingest

import com.pulseboard.core.Alert
import com.pulseboard.core.Event
import com.pulseboard.core.StatsService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class EventBus(
    @Autowired private val statsService: StatsService,
) {
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
        statsService.recordEvent()
    }

    suspend fun publishAlert(alert: Alert) {
        _alerts.emit(alert)
        statsService.recordAlert()
    }

    fun tryPublishEvent(event: Event): Boolean {
        val result = _events.tryEmit(event)
        if (result) {
            statsService.recordEvent()
        }
        return result
    }

    fun tryPublishAlert(alert: Alert): Boolean {
        val result = _alerts.tryEmit(alert)
        if (result) {
            statsService.recordAlert()
        }
        return result
    }
}
