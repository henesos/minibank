package com.minibank.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Request Logging Filter
 * 
 * Logs all incoming requests with:
 * - Unique request ID
 * - HTTP method and path
 * - Response status and duration
 */
@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String REQUEST_START_TIME = "requestStartTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Generate or use existing request ID
        String requestId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        // Store start time
        exchange.getAttributes().put(REQUEST_START_TIME, System.currentTimeMillis());

        // Add request ID to headers
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        String finalRequestId = requestId;
        log.info("[{}] --> {} {}", finalRequestId, request.getMethod(), request.getPath());

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    Long startTime = exchange.getAttribute(REQUEST_START_TIME);
                    if (startTime != null) {
                        long duration = System.currentTimeMillis() - startTime;
                        int statusCode = exchange.getResponse().getStatusCode() != null
                                ? exchange.getResponse().getStatusCode().value()
                                : 0;
                        log.info("[{}] <-- {} ({}ms)", finalRequestId, statusCode, duration);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return -200; // Execute first
    }
}
