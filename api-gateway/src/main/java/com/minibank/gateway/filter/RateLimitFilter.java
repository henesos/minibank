package com.minibank.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Rate Limit Filter
 * 
 * Rate limiting is primarily handled by Spring Cloud Gateway's built-in
 * RequestRateLimiter filter with Redis. This filter adds custom headers
 * and handles rate limit exceeded responses.
 * 
 * Configuration is done in application.yml:
 * - Default: 100 requests per second per IP
 * - Burst capacity: 200 requests
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(java.lang.RuntimeException.class, e -> {
                    if (e.getMessage() != null && e.getMessage().contains("Rate Limit Exceeded")) {
                        log.warn("Rate limit exceeded for: {}", 
                                exchange.getRequest().getRemoteAddress());
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        exchange.getResponse().getHeaders().add("Content-Type", "application/json");
                        
                        String errorBody = """
                                {
                                    "status": 429,
                                    "error": "Too Many Requests",
                                    "message": "Rate limit exceeded. Please try again later.",
                                    "path": "%s"
                                }
                                """.formatted(exchange.getRequest().getPath().value());
                        
                        return exchange.getResponse()
                                .writeWith(Mono.just(exchange.getResponse()
                                        .bufferFactory()
                                        .wrap(errorBody.getBytes())));
                    }
                    return Mono.error(e);
                });
    }

    @Override
    public int getOrder() {
        return -50; // After authentication
    }
}
