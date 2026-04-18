package com.minibank.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * MiniBank API Gateway Application
 * 
 * Features:
 * - Spring Cloud Gateway for routing
 * - JWT Authentication for all services
 * - Rate limiting with Redis
 * - CORS configuration
 * - Request logging
 * - Distributed tracing with Zipkin
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }

    /**
     * Key resolver for rate limiting.
     * Uses client IP address as the key.
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : UUID.randomUUID().toString();
            return Mono.just(ip);
        };
    }
}
