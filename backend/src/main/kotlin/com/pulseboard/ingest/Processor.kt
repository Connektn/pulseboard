package com.pulseboard.ingest

import com.pulseboard.core.Event
import com.pulseboard.core.Rules
import com.pulseboard.core.WindowStore
import com.pulseboard.transport.EventTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class Processor(
    private val eventTransport: EventTransport,
    private val eventBus: EventBus,
    private val windowStore: WindowStore,
    private val rules: Rules,
) {
    private val logger = LoggerFactory.getLogger(Processor::class.java)
    private var processingJob: Job? = null

    /**
     * Start the processor pipeline when the application is ready
     */
    @EventListener(ApplicationReadyEvent::class)
    fun startProcessing() {
        logger.info("Starting event processor pipeline...")
        startProcessing(CoroutineScope(kotlinx.coroutines.Dispatchers.Default))
    }

    /**
     * Start the processor pipeline with a specific coroutine scope
     */
    fun startProcessing(scope: CoroutineScope) {
        // Stop any existing processing
        stopProcessing()

        processingJob =
            scope.launch {
                logger.info("Event processor pipeline started")
                try {
                    eventTransport.subscribeToEvents()
                        .onEach { event ->
                            processEvent(event)
                        }
                        .collect()
                } catch (e: Exception) {
                    logger.error("Error in event processing pipeline", e)
                    throw e
                }
            }
    }

    /**
     * Stop the processor pipeline
     */
    fun stopProcessing() {
        processingJob?.cancel()
        processingJob = null
        logger.info("Event processor pipeline stopped")
    }

    /**
     * Check if the processor is currently running
     */
    fun isRunning(): Boolean = processingJob?.isActive == true

    /**
     * Process a single event through the pipeline:
     * 1. Update window store with event data
     * 2. Evaluate rules against the event
     * 3. Publish any triggered alerts
     */
    private suspend fun processEvent(event: Event) {
        try {
            logger.debug(
                "Processing event: type={}, entityId={}, profile={}, ts={}",
                event.type,
                event.entityId,
                event.profile,
                event.ts,
            )

            // Step 1: Update window store with event data
            updateWindowStore(event)

            // Step 2: Evaluate rules against the event
            val alerts = rules.evaluateAll(event)

            // Step 3: Publish any triggered alerts
            alerts.forEach { alert ->
                val published = eventBus.tryPublishAlert(alert)
                if (published) {
                    logger.info(
                        "Alert published: rule={}, entityId={}, severity={}, id={}",
                        alert.rule,
                        alert.entityId,
                        alert.severity,
                        alert.id,
                    )
                } else {
                    logger.warn(
                        "Failed to publish alert: rule={}, entityId={}, id={}",
                        alert.rule,
                        alert.entityId,
                        alert.id,
                    )
                }
            }

            if (alerts.isNotEmpty()) {
                logger.debug("Processed event generated {} alerts", alerts.size)
            }
        } catch (e: Exception) {
            logger.error(
                "Error processing event: type={}, entityId={}, error={}",
                event.type,
                event.entityId,
                e.message,
                e,
            )
            // Don't rethrow - we want to continue processing other events
        }
    }

    /**
     * Update the window store with event data
     */
    private fun updateWindowStore(event: Event) {
        try {
            // Add the event to the appropriate time series window
            // Use value if present, otherwise default to 1 for counting
            val value = event.value ?: 1L
            windowStore.append(event.entityId, event.type, event.ts, value)

            logger.trace(
                "Updated window store: entityId={}, type={}, value={}, ts={}",
                event.entityId,
                event.type,
                value,
                event.ts,
            )
        } catch (e: Exception) {
            logger.error(
                "Error updating window store for event: entityId={}, type={}, error={}",
                event.entityId,
                event.type,
                e.message,
                e,
            )
            // Don't rethrow - we want to continue processing other events
        }
    }

    /**
     * Get processing statistics
     */
    fun getProcessingStats(): Map<String, Any> {
        return mapOf(
            "isRunning" to isRunning(),
            "processingJobActive" to (processingJob?.isActive ?: false),
            "processingJobCompleted" to (processingJob?.isCompleted ?: true),
            "processingJobCancelled" to (processingJob?.isCancelled ?: false),
        )
    }
}
