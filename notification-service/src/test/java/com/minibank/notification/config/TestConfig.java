package com.minibank.notification.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration to disable Kafka for integration tests.
 */
@Configuration
@Profile("test")
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class
})
public class TestConfig {
    // This configuration disables Kafka auto-configuration in test profile
}
