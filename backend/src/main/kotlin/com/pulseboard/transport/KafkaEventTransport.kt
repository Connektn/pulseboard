package com.pulseboard.transport

import com.pulseboard.core.Event
import com.pulseboard.ingest.EventBus
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
 * Publishes events to Kafka topic and consumes them for processing.
 */
@Component
@ConditionalOnProperty(value = ["transport.mode"], havingValue = "kafka")
class KafkaEventTransport(
    private val kafkaTemplate: KafkaTemplate<String, Event>,
    private val eventBus: EventBus,
    @Value("\${spring.kafka.topics.events}") private val eventsTopic: String,
) : EventTransport {
    private val logger = LoggerFactory.getLogger(KafkaEventTransport::class.java)

    private val jobScope: CoroutineScope
        get() = CoroutineScope(Job() + Dispatchers.IO)

    override suspend fun publishEvent(event: Event) {
        try {
            kafkaTemplate.send(eventsTopic, event.entityId, event)
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

    override fun subscribeToEvents(): Flow<Event> {
        return eventBus.events
    }

    @KafkaListener(
        topics = ["\${spring.kafka.topics.events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
    )
    fun consumeEvent(
        record: ConsumerRecord<String, Event>,
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
