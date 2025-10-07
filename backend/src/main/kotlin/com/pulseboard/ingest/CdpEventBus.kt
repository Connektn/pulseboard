package com.pulseboard.ingest

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.core.StatsService
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class CdpEventBus(
    @Autowired private val statsService: StatsService,
) : EventBus<CdpEvent> {
    private val _events =
        MutableSharedFlow<CdpEvent>(
            replay = 0,
            extraBufferCapacity = 1000,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events: SharedFlow<CdpEvent> = _events.asSharedFlow()

    override suspend fun publishEvent(event: CdpEvent) {
        _events.emit(event)
        statsService.recordEvent()
    }

    override fun tryPublishEvent(event: CdpEvent): Boolean {
        val result = _events.tryEmit(event)
        if (result) {
            statsService.recordEvent()
        }
        return result
    }
}
