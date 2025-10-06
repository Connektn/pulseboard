package com.pulseboard

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Instant

// Fixed clock for testing purposes
val fixedClock = Clock.fixed(Instant.now(), java.time.ZoneOffset.UTC)

val testMeterRegistry = SimpleMeterRegistry()
