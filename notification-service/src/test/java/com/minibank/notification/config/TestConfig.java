package com.minibank.notification.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.mockito.Mockito.mock;

/**
 * Test configuration to disable Kafka and provide mock Redis for integration tests.
 */
@Configuration
@Profile("test")
@EnableAutoConfiguration(exclude = {
    KafkaAutoConfiguration.class
})
public class TestConfig {

    /**
     * Provides a mock RedisTemplate for tests that depend on Redis
     * (e.g. TransactionEventConsumer) without requiring a real Redis instance.
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(mock(RedisConnectionFactory.class));
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
