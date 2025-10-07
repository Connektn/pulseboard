package com.pulseboard.transport

import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.ingest.CdpEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * Kafka-based event transport implementation.
 * Publishes events to the Kafka topic and consumes them for processing.
 */
@Component
@ConditionalOnProperty(value = ["transport.mode"], havingValue = "kafka")
class KafkaCdpEventTransport(
    private val cdpEventKafkaTemplate: KafkaTemplate<String, CdpEvent>,
    private val eventBus: CdpEventBus,
    @Value("\${spring.kafka.topics.cdp-events}") private val eventsTopic: String,
) : CdpEventTransport {
    private val logger = LoggerFactory.getLogger(KafkaCdpEventTransport::class.java)

    private val jobScope: CoroutineScope
        get() = CoroutineScope(Job() + Dispatchers.IO)

    override suspend fun publishEvent(event: CdpEvent) {
        try {
            cdpEventKafkaTemplate.send(eventsTopic, event.key(), event)
                .whenComplete { result, exception ->
                    if (exception != null) {
                        logger.error("Failed to send event to Kafka", exception)
                    } else {
                        logger.debug(
                            "Event sent to Kafka: topic={}, partition={}, offset={}",
                            result.recordMetadata.topic(),
                            result.recordMetadata.partition(),
                            result.recordMetadata.offset(),
                        )
                    }
                }
        } catch (e: Exception) {
            logger.error("Error publishing event to Kafka", e)
            throw e
        }
    }

    override fun subscribeToEvents(): Flow<CdpEvent> {
        return eventBus.events
    }

    @KafkaListener(
        topics = ["\${spring.kafka.topics.cdp-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "cdpEventKafkaListenerContainerFactory",
    )
    fun consumeEvent(
        record: ConsumerRecord<String, CdpEvent>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val event = record.value() ?: return
            logger.debug("Received event from Kafka: {}", event)

            jobScope.launch {
                // Publish to in-memory event bus for processing
                eventBus.publishEvent(event)
            }

            // Acknowledge the message
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            logger.error("Error processing Kafka event", e)
            // Don't acknowledge on error to allow for retry
        }
    }

    override fun getTransportType(): TransportType = TransportType.KAFKA
}
