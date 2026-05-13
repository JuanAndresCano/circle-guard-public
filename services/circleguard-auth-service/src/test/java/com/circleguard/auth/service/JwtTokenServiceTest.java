package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtTokenServiceTest {

    private static final String SECRET = "my-super-secret-dev-key-32-chars-long-12345678";
    private static final long EXPIRATION = 3600000L;
    private final String testSecret = "my-super-secret-dev-key-32-chars-long-12345678";

    private JwtTokenService jwtTokenService;
    private Key verificationKey;

    @Mock
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(SECRET, EXPIRATION);
        verificationKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    @Test
    @DisplayName("U1: Token generado tiene formato JWT válido (3 partes)")
    void test1_generateToken_ReturnsValidFormat() {
        UUID anonymousId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, authentication);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "JWT debe tener 3 partes separadas por puntos");
    }

    @Test
    @DisplayName("U2: Token incluye roles correctamente en claims")
    void test2_generateToken_IncludesRolesCorrectly() {
        UUID rawId = UUID.randomUUID();
        GrantedAuthority authority = () -> "ROLE_HEALTH_CENTER";
        lenient().doReturn(List.of(authority)).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(rawId, authentication);

        assertNotNull(token);
        assertTrue(token.length() > 20);
    }

    @Test
    @DisplayName("U3: Token con authorities null lanza NullPointerException")
    void test3_generateToken_WithNullAuthorities_ThrowsException() {
        UUID rawId = UUID.randomUUID();
        lenient().doReturn(null).when(authentication).getAuthorities();

        assertThrows(NullPointerException.class, () ->
            jwtTokenService.generateToken(rawId, authentication)
        );
    }

    @Test
    @DisplayName("U4: Dos usuarios distintos generan tokens distintos")
    void test4_generateToken_DifferentTokensForDifferentUsers() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token1 = jwtTokenService.generateToken(id1, authentication);
        String token2 = jwtTokenService.generateToken(id2, authentication);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("U5: Token con authorities vacías se genera sin error")
    void test5_generateToken_HandlesEmptyAuthorities() {
        UUID rawId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(rawId, authentication);
        assertNotNull(token);
    }

    // --- Tests nuevos ---

    @Test
    @DisplayName("U6: Subject del token corresponde al anonymousId del usuario")
    void test6_generateToken_SubjectMatchesAnonymousId() {
        UUID anonymousId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, authentication);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    @DisplayName("U7: Claims del token contienen lista de permisos correcta")
    void test7_generateToken_PermissionsClaimContainsAllAuthorities() {
        UUID anonymousId = UUID.randomUUID();
        GrantedAuthority role = () -> "ROLE_GATE_STAFF";
        GrantedAuthority perm = () -> "gate:scan";
        lenient().doReturn(List.of(role, perm)).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, authentication);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        @SuppressWarnings("unchecked")
        List<String> permissions = (List<String>) claims.get("permissions");
        assertNotNull(permissions);
        assertTrue(permissions.contains("ROLE_GATE_STAFF"));
        assertTrue(permissions.contains("gate:scan"));
    }

    @Test
    @DisplayName("U8: Token tiene fecha de expiración en el futuro")
    void test8_generateToken_ExpirationIsInFuture() {
        UUID anonymousId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        long beforeGeneration = System.currentTimeMillis();
        String token = jwtTokenService.generateToken(anonymousId, authentication);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertTrue(claims.getExpiration().getTime() > beforeGeneration);
        assertTrue(claims.getExpiration().getTime() <= beforeGeneration + EXPIRATION + 1000);
    }

    @Test
    @DisplayName("U9: Token firmado con clave incorrecta no es válido")
    void test9_generateToken_CannotBeVerifiedWithWrongKey() {
        UUID anonymousId = UUID.randomUUID();
        lenient().doReturn(List.of()).when(authentication).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, authentication);
        Key wrongKey = Keys.hmacShaKeyFor("wrong-key-that-is-at-least-32-chars-xxxx".getBytes());

        assertThrows(Exception.class, () ->
            Jwts.parserBuilder()
                .setSigningKey(wrongKey)
                .build()
                .parseClaimsJws(token)
        );
    }

    @Test
    @DisplayName("U10: generates valid JWT containing user roles and permissions")
    void testGenerateToken_ShouldReturnValidJwt() {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = mock(Authentication.class);
        
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                new SimpleGrantedAuthority("post:create")
        );
        doReturn(authorities).when(auth).getAuthorities();

        String token = jwtTokenService.generateToken(anonymousId, auth);

        assertNotNull(token);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(testSecret.getBytes())
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
        List<String> permissions = claims.get("permissions", List.class);
        assertTrue(permissions.contains("ROLE_USER"));
        assertTrue(permissions.contains("post:create"));
    }
}