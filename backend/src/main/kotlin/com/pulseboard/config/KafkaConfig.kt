package com.pulseboard.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseboard.cdp.model.CdpEvent
import com.pulseboard.core.EntityEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.kafka.support.serializer.JsonSerializer

/**
 * Kafka configuration for both EntityEvent and CdpEvent types.
 */
@Configuration
@ConditionalOnProperty(value = ["transport.mode"], havingValue = "kafka")
class KafkaConfig(
    @Value("\${spring.kafka.bootstrap-servers}") private val bootstrapServers: String,
    @Value("\${spring.kafka.consumer.group-id}") private val groupId: String,
    private val objectMapper: ObjectMapper,
) {
    // ===== EntityEvent Configuration =====

    @Bean
    fun entityEventProducerFactory(): ProducerFactory<String, EntityEvent> {
        val configProps =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
            )
        val producerFactory = DefaultKafkaProducerFactory<String, EntityEvent>(configProps)
        producerFactory.valueSerializer = JsonSerializer(objectMapper)
        return producerFactory
    }

    @Bean
    fun entityEventKafkaTemplate(): KafkaTemplate<String, EntityEvent> {
        return KafkaTemplate(entityEventProducerFactory())
    }

    @Bean
    fun entityEventConsumerFactory(): ConsumerFactory<String, EntityEvent> {
        val configProps =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS to StringDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
                JsonDeserializer.VALUE_DEFAULT_TYPE to EntityEvent::class.java.name,
                JsonDeserializer.TRUSTED_PACKAGES to "com.pulseboard.core",
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        val consumerFactory = DefaultKafkaConsumerFactory<String, EntityEvent>(configProps)
        consumerFactory.setValueDeserializer(JsonDeserializer(EntityEvent::class.java, objectMapper))
        return consumerFactory
    }

    @Bean
    fun entityEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, EntityEvent> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, EntityEvent>()
        factory.consumerFactory = entityEventConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        return factory
    }

    // ===== CdpEvent Configuration =====

    @Bean
    fun cdpEventProducerFactory(): ProducerFactory<String, CdpEvent> {
        val configProps =
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
                JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
            )
        val producerFactory = DefaultKafkaProducerFactory<String, CdpEvent>(configProps)
        producerFactory.valueSerializer = JsonSerializer(objectMapper)
        return producerFactory
    }

    @Bean
    fun cdpEventKafkaTemplate(): KafkaTemplate<String, CdpEvent> {
        return KafkaTemplate(cdpEventProducerFactory())
    }

    @Bean
    fun cdpEventConsumerFactory(): ConsumerFactory<String, CdpEvent> {
        val configProps =
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ErrorHandlingDeserializer::class.java,
                ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS to StringDeserializer::class.java,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS to JsonDeserializer::class.java,
                JsonDeserializer.VALUE_DEFAULT_TYPE to CdpEvent::class.java.name,
                JsonDeserializer.TRUSTED_PACKAGES to "com.pulseboard.cdp.model",
                JsonDeserializer.USE_TYPE_INFO_HEADERS to false,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
        val consumerFactory = DefaultKafkaConsumerFactory<String, CdpEvent>(configProps)
        consumerFactory.setValueDeserializer(JsonDeserializer(CdpEvent::class.java, objectMapper))
        return consumerFactory
    }

    @Bean
    fun cdpEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, CdpEvent> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, CdpEvent>()
        factory.consumerFactory = cdpEventConsumerFactory()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
        return factory
    }
}
