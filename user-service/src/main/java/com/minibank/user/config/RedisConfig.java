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

    @Value("${app.cache.user-ttl:300}")
    private Long userTtl;

    @Value("${app.cache.session-ttl:1800}")
    private Long sessionTtl;

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

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Configure specific cache TTLs
        java.util.Map<String, RedisCacheConfiguration> cacheConfigurations = new java.util.HashMap<>();
        
        // User profiles: 5 minutes TTL
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofSeconds(userTtl)));
        
        // Sessions: 30 minutes TTL
        cacheConfigurations.put("sessions", defaultConfig.entryTtl(Duration.ofSeconds(sessionTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
