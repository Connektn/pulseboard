package com.pulseboard.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Metrics configuration for Micrometer.
 *
 * Provides a simple in-memory MeterRegistry for metrics collection.
 * In production, this could be replaced with a more sophisticated registry
 * (Prometheus, Datadog, etc.) via Spring Boot Actuator.
 */
@Configuration
class MetricsConfig {
    @Bean
    fun meterRegistry(): MeterRegistry {
        return SimpleMeterRegistry()
    }
}
