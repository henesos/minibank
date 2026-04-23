package com.minibank.gateway.unit;

import com.minibank.gateway.filter.InternalTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class InternalTokenGeneratorTest {

    private InternalTokenGenerator generator;
    private static final String TEST_SECRET = "test-internal-secret-key-123456789012";
    private static final long TEST_TTL = 300000L;

    @BeforeEach
    void setUp() {
        generator = new InternalTokenGenerator();
        ReflectionTestUtils.setField(generator, "internalSecret", TEST_SECRET);
        ReflectionTestUtils.setField(generator, "tokenTtl", TEST_TTL);
    }

    @Test
    void generateToken_shouldReturnValidToken() {
        String token = generator.generateToken("/api/v1/accounts");

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void generateToken_tokenFormatShouldContainTimestamp() throws InterruptedException {
        String token1 = generator.generateToken("/api/v1/accounts");
        Thread.sleep(1);
        String token2 = generator.generateToken("/api/v1/accounts");

        assertNotEquals(token1, token2);
    }
}