package com.minibank.gateway.config;

import com.minibank.gateway.handler.FallbackHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Router configuration for fallback endpoints
 */
@Configuration
public class FallbackRouterConfig {

    @Bean
    public RouterFunction<ServerResponse> fallbackRouter(FallbackHandler fallbackHandler) {
        return RouterFunctions.route()
                .GET("/fallback/user", fallbackHandler::userServiceFallback)
                .GET("/fallback/account", fallbackHandler::accountServiceFallback)
                .GET("/fallback/transaction", fallbackHandler::transactionServiceFallback)
                .GET("/fallback/notification", fallbackHandler::notificationServiceFallback)
                .POST("/fallback/user", fallbackHandler::userServiceFallback)
                .POST("/fallback/account", fallbackHandler::accountServiceFallback)
                .POST("/fallback/transaction", fallbackHandler::transactionServiceFallback)
                .POST("/fallback/notification", fallbackHandler::notificationServiceFallback)
                .build();
    }
}
