package com.pulseboard.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Application configuration class.
 *
 * This class can be used to define beans and configuration settings
 * for the application context.
 */
@Configuration
class AppConfig {

    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
