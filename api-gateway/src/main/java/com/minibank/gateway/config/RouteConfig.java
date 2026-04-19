package com.minibank.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway Route Configuration
 * 
 * Routes all requests to appropriate microservices:
 * - /api/users/** -> User Service (8081)
 * - /api/accounts/** -> Account Service (8082)
 * - /api/transactions/** -> Transaction Service (8083)
 * - /api/notifications/** -> Notification Service (8084)
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
                // User Service Routes
                .route("user-service", r -> r
                        .path("/api/users/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway"))
                        .uri(userServiceUrl))

                // Authentication routes (public)
                .route("auth-service", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway"))
                        .uri(userServiceUrl))

                // Account Service Routes
                .route("account-service", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway"))
                        .uri(accountServiceUrl))

                // Transaction Service Routes
                .route("transaction-service", r -> r
                        .path("/api/transactions/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway"))
                        .uri(transactionServiceUrl))

                // Notification Service Routes
                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .filters(f -> f
                                .addRequestHeader("X-Gateway", "MiniBank-API-Gateway"))
                        .uri(notificationServiceUrl))

                // Swagger UI Aggregation routes
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
