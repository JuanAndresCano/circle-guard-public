package com.circleguard.gateway.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for JWT validation through Gateway security filters.
 *
 * Validates:
 * - Valid JWT authentication
 * - Expired token rejection
 * - Malformed token rejection
 * - Signature tampering protection
 * - Permission propagation
 */
@SpringBootTest
@AutoConfigureMockMvc
class GatewayJwtValidationIntegrationTest {

    private static final String VALIDATION_ENDPOINT = "/api/v1/gate/validate";

    private static final Duration VALID_TOKEN_DURATION = Duration.ofHours(1);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @Value("${qr.secret}")
    private String qrSecret;

    private Key signingKey;

    @BeforeEach
    void setup() {

        signingKey = Keys.hmacShaKeyFor(qrSecret.getBytes());

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(anyString()))
                .thenReturn("GREEN");
    }

    @Test
    @DisplayName("Should validate a correct JWT token")
    void shouldValidateCorrectJwtToken() throws Exception {

        String token = createValidJWT(
                "test-user-123",
                List.of("VISITOR")
        );

        performValidationRequest(token)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject malformed JWT tokens")
    void shouldRejectMalformedJwtToken() throws Exception {

        performValidationRequest("invalid.token.format")
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject expired JWT tokens")
    void shouldRejectExpiredJwtToken() throws Exception {

        String expiredToken = createExpiredJWT("test-user-456");

        performValidationRequest(expiredToken)
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should reject requests without token")
    void shouldRejectRequestWithoutToken() throws Exception {

        mockMvc.perform(post(VALIDATION_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should validate JWT tokens with multiple permissions")
    void shouldValidateJwtWithMultiplePermissions() throws Exception {

        String token = createValidJWT(
                "health-center-user",
                List.of(
                        "identity:lookup",
                        "survey:validate",
                        "admin:access"
                )
        );

        performValidationRequest(token)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should reject tampered JWT signatures")
    void shouldRejectTamperedJwtSignature() throws Exception {

        String validToken = createValidJWT(
                "trusted-user",
                List.of("VISITOR")
        );

        String tamperedToken = tamperToken(validToken);

        performValidationRequest(tamperedToken)
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String createValidJWT(
            String subject,
            List<String> permissions
    ) {

        Instant now = Instant.now();

        return Jwts.builder()
                .setSubject(subject)
                .claim("permissions", permissions)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(
                        now.plus(VALID_TOKEN_DURATION)
                ))
                .signWith(signingKey)
                .compact();
    }

    private String createExpiredJWT(String subject) {

        Instant now = Instant.now();

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now.minus(Duration.ofHours(2))))
                .setExpiration(Date.from(now.minus(Duration.ofHours(1))))
                .signWith(signingKey)
                .compact();
    }

    private String tamperToken(String token) {

        char lastCharacter = token.charAt(token.length() - 1);

        char replacement =
                lastCharacter == 'A'
                        ? 'B'
                        : 'A';

        return token.substring(0, token.length() - 1)
                + replacement;
    }

    private org.springframework.test.web.servlet.ResultActions performValidationRequest(
            String token
    ) throws Exception {

        String requestBody = buildValidationPayload(token);

        return mockMvc.perform(
                post(VALIDATION_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
        );
    }

    private String buildValidationPayload(String token) {

        return """
                {
                    "token": "%s"
                }
                """.formatted(token);
    }
}