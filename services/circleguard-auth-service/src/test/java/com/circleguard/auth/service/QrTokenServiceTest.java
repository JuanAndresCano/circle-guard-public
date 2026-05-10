package com.circleguard.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QrTokenServiceTest {

    private static final String QR_SECRET = "my-qr-secret-key-for-dev-1234567890";
    // 300 segundos = 300000 ms
    private static final long QR_EXPIRATION = 300000L;

    private QrTokenService qrTokenService;
    private Key verificationKey;

    @BeforeEach
    void setUp() {
        qrTokenService = new QrTokenService(QR_SECRET, QR_EXPIRATION);
        verificationKey = Keys.hmacShaKeyFor(QR_SECRET.getBytes());
    }

    @Test
    @DisplayName("U10: QR token generado tiene formato JWT válido")
    void test1_generateQrToken_ReturnsValidJwtFormat() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "QR token debe ser un JWT con 3 partes");
    }

    @Test
    @DisplayName("U11: Subject del QR token corresponde al anonymousId")
    void test2_generateQrToken_SubjectMatchesAnonymousId() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertEquals(anonymousId.toString(), claims.getSubject());
    }

    @Test
    @DisplayName("U12: QR token no está expirado al momento de generación")
    void test3_generateQrToken_IsNotExpiredOnCreation() {
        UUID anonymousId = UUID.randomUUID();

        String token = qrTokenService.generateQrToken(anonymousId);

        Claims claims = Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
                .getBody();

        assertTrue(claims.getExpiration().getTime() > System.currentTimeMillis());
    }

    @Test
    @DisplayName("U13: QR tokens distintos para distintos anonymousIds")
    void test4_generateQrToken_DifferentTokensForDifferentIds() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        String token1 = qrTokenService.generateQrToken(id1);
        String token2 = qrTokenService.generateQrToken(id2);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("U14: QR token expirado inmediatamente con expiration=1ms no es válido")
    void test5_generateQrToken_ExpiredTokenFailsVerification() throws InterruptedException {
        QrTokenService shortLivedService = new QrTokenService(QR_SECRET, 1L);
        UUID anonymousId = UUID.randomUUID();

        String token = shortLivedService.generateQrToken(anonymousId);
        Thread.sleep(50);

        assertThrows(Exception.class, () ->
            Jwts.parserBuilder()
                .setSigningKey(verificationKey)
                .build()
                .parseClaimsJws(token)
        );
    }

    @Test
    @DisplayName("U15: QR token firmado con clave incorrecta no es verificable")
    void test6_generateQrToken_WrongKeyFailsVerification() {
        UUID anonymousId = UUID.randomUUID();
        Key wrongKey = Keys.hmacShaKeyFor("wrong-qr-secret-key-minimum-32-chars-xx".getBytes());

        String token = qrTokenService.generateQrToken(anonymousId);

        assertThrows(Exception.class, () ->
            Jwts.parserBuilder()
                .setSigningKey(wrongKey)
                .build()
                .parseClaimsJws(token)
        );
    }
}