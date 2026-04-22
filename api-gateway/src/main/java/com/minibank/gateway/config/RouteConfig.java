package com.minibank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Route Configuration
 *
 * Routes all requests to appropriate microservices with circuit breaker protection:
 * - /api/v1/users/** -> User Service (8081)
 * - /api/v1/accounts/** -> Account Service (8082)
 * - /api/v1/transactions/** -> Transaction Service (8083)
 * - /api/v1/notifications/** -> Notification Service (8084)
 *
 * Legacy /api/{resource}/** routes have been removed. All clients must use /api/v1/ paths.
 *
 * Each route is protected with a Resilience4j circuit breaker that falls back
 * to the corresponding endpoint in FallbackRouterConfig when the downstream
 * service is unavailable.
 */
@Configuration
public class RouteConfig {

    @Value("${services.user-service.url}")
    private String userServiceUrl;

    @Value("${services.account-service.url}")
    private String accountServiceUrl;

    @Value("${services.transaction-service.url}")
    private String transactionServiceUrl;

    @Value("${services.notification-service.url}")
    private String notificationServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // User Service Routes (v1 API) — circuit breaker protected
                .route("user-service", r -> r
                        .path("/api/v1/users/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway")
                                .circuitBreaker(c -> c
                                        .setName("user-service")
                                        .setFallbackUri("forward:/fallback/user")))
                        .uri(userServiceUrl))

                // Account Service Routes (v1 API) — circuit breaker protected
                .route("account-service", r -> r
                        .path("/api/v1/accounts/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway")
                                .circuitBreaker(c -> c
                                        .setName("account-service")
                                        .setFallbackUri("forward:/fallback/account")))
                        .uri(accountServiceUrl))

                // Transaction Service Routes (v1 API) — circuit breaker protected
                .route("transaction-service", r -> r
                        .path("/api/v1/transactions/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway")
                                .circuitBreaker(c -> c
                                        .setName("transaction-service")
                                        .setFallbackUri("forward:/fallback/transaction")))
                        .uri(transactionServiceUrl))

                // Notification Service Routes (v1 API) — circuit breaker protected
                .route("notification-service", r -> r
                        .path("/api/v1/notifications/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway")
                                .circuitBreaker(c -> c
                                        .setName("notification-service")
                                        .setFallbackUri("forward:/fallback/notification")))
                        .uri(notificationServiceUrl))

                // Swagger UI Aggregation routes (no circuit breaker — static content)
                .route("user-service-swagger", r -> r
                        .path("/swagger-user/**")
                        .filters(f -> f
                                .rewritePath("/swagger-user/(?<segment>.*)", "/${segment}"))
                        .uri(userServiceUrl))

                .route("account-service-swagger", r -> r
                        .path("/swagger-account/**")
                        .filters(f -> f
                                .rewritePath("/swagger-account/(?<segment>.*)", "/${segment}"))
                        .uri(accountServiceUrl))

                .route("transaction-service-swagger", r -> r
                        .path("/swagger-transaction/**")
                        .filters(f -> f
                                .rewritePath("/swagger-transaction/(?<segment>.*)", "/${segment}"))
                        .uri(transactionServiceUrl))

                .route("notification-service-swagger", r -> r
                        .path("/swagger-notification/**")
                        .filters(f -> f
                                .rewritePath("/swagger-notification/(?<segment>.*)", "/${segment}"))
                        .uri(notificationServiceUrl))

                .build();
    }
}
