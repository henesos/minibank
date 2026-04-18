package com.minibank.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Kafka configuration for non-test profiles.
 * Kafka is disabled in test profile to avoid external dependency.
 */
@Configuration
@Profile("!test")
@EnableKafka
public class KafkaEnableConfig {
    // This configuration enables Kafka only when NOT in test profile
}
