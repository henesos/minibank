package com.minibank.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for caching and session management.
 *
 * Cache Strategy:
 * - User profiles: 5 minutes TTL
 * - Sessions: 30 minutes TTL
 * - Balance: NEVER cached (handled by Account Service)
 */
@Configuration
public class RedisConfig {

    private static final int CACHE_TTL_MINUTES = 5;

    @Value("${app.cache.user-ttl:300}")
    private Long userTtl;

    @Value("${app.cache.session-ttl:1800}")
    private Long sessionTtl;

    /** Redis template bean for caching and session management. */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /** Cache manager bean with per-cache TTL configuration. */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Configure specific cache TTLs
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User profiles: 5 minutes TTL
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofSeconds(userTtl)));

        // Sessions: 30 minutes TTL
        cacheConfigurations.put("sessions", defaultConfig.entryTtl(Duration.ofSeconds(sessionTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(CACHE_TTL_MINUTES)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
