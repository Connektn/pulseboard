package com.pulseboard.transport

import com.pulseboard.core.EntityEvent
import com.pulseboard.ingest.EntityEventBus
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
class KafkaEntityEventTransport(
    private val entityEventKafkaTemplate: KafkaTemplate<String, EntityEvent>,
    private val eventBus: EntityEventBus,
    @Value("\${spring.kafka.topics.entity-events}") private val eventsTopic: String,
) : EntityEventTransport {
    private val logger = LoggerFactory.getLogger(KafkaEntityEventTransport::class.java)

    private val jobScope: CoroutineScope
        get() = CoroutineScope(Job() + Dispatchers.IO)

    override suspend fun publishEvent(event: EntityEvent) {
        try {
            entityEventKafkaTemplate.send(eventsTopic, event.payload.entityId, event)
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

    override fun subscribeToEvents(): Flow<EntityEvent> {
        return eventBus.events
    }

    @KafkaListener(
        topics = ["\${spring.kafka.topics.entity-events}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "entityEventKafkaListenerContainerFactory",
    )
    fun consumeEvent(
        record: ConsumerRecord<String, EntityEvent>,
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
