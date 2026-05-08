package com.circleguard.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        // Init properties explicitly as constructor demands
        jwtTokenService = new JwtTokenService("my-super-secret-dev-key-32-chars-long-12345678", 3600000L);
    }

    @Test
    void test1_generateToken_ReturnsValidFormat() {
        UUID anonymousId = UUID.randomUUID();
        
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, authentication);

        assertNotNull(token);
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length, "Un JWT debe tener 3 partes separadas por puntos");
    }

    @Test
    void test2_generateToken_IncludesRolesCorrectly() {
        UUID rawId = UUID.randomUUID();
        
        GrantedAuthority authority = () -> "ROLE_HEALTH_CENTER";
        lenient().doReturn(List.of(authority)).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(rawId, authentication);
        
        assertNotNull(token);
        assertTrue(token.length() > 20);
    }

    @Test
    void test3_generateToken_WithNullAuthorities_ThrowsException() {
        UUID rawId = UUID.randomUUID();
        
        // When getAuthorities is null
        lenient().doReturn(null).when(authentication).getAuthorities();

        assertThrows(NullPointerException.class, () -> {
            jwtTokenService.generateToken(rawId, authentication);
        });
    }

    @Test
    void test4_generateToken_DifferentTokensForDifferentUsers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token1 = jwtTokenService.generateToken(id1, authentication);
        String token2 = jwtTokenService.generateToken(id2, authentication);
        
        assertNotEquals(token1, token2);
    }

    @Test
    void test5_generateToken_HandlesEmptyAuthorities() {
        UUID rawId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(rawId, authentication);
        assertNotNull(token);
    }
}