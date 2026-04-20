package com.minibank.gateway.filter;

// JwtUtil is in the same package now
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Authentication filter for API Gateway.
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    @Autowired
    private JwtUtil jwtUtil;

    /** Constructor. */
    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Allow OPTIONS requests for CORS preflight
            if ("OPTIONS".equalsIgnoreCase(request.getMethod().name())) {
                return chain.filter(exchange);
            }

            // Check for Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            if (!StringUtils.hasText(authHeader)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            String token = jwtUtil.extractTokenFromHeader(authHeader);

            if (token == null || !jwtUtil.validateToken(token)) {
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // Add user info to headers for downstream services
            String username = jwtUtil.extractUsername(token);
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Username", username)
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    /** Filter configuration class. */
    public static class Config {
        // Configuration properties if needed
    }
}
