package com.minibank.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration for Notification Service.
 *
 * <p>Provides a {@link RedisTemplate} bean with {@link StringRedisSerializer}
 * for both keys and values, used primarily for idempotency tracking in
 * {@link com.minibank.notification.kafka.TransactionEventConsumer}.</p>
 *
 * <p>Redis connection is already configured via {@code spring.redis.*}
 * properties in application.yml.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a RedisTemplate with String serializers for keys and values.
     *
     * <p>String serialization is used because idempotency keys and values
     * are simple strings (e.g., "notification:event:{eventId}" → "1").</p>
     *
     * @param connectionFactory the Redis connection factory (auto-configured by Spring Boot)
     * @return configured RedisTemplate instance
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
