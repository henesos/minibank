package com.minibank.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;

/**
 * JWT Utility for API Gateway.
 *
 * <p>Uses jjwt 0.12.x API (verifyWith, parseSignedClaims, getPayload).</p>
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static final int BEARER_PREFIX_LENGTH = 7;

    /** Gets the HMAC signing key. */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** Extracts all claims from JWT token. */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Extracts username (subject) from JWT token. */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** Extracts expiration date from JWT token. */
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    /** Checks if the token is expired. */
    public Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /** Validates the token. */
    public Boolean validateToken(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /** Extracts the token from Authorization header. */
    public String extractTokenFromHeader(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(BEARER_PREFIX_LENGTH);
        }
        return null;
    }
}
