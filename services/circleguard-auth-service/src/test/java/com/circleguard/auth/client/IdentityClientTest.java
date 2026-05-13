package com.circleguard.auth.client;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import org.springframework.web.client.RestTemplate;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class IdentityClientTest {

    private IdentityClient identityClient;

        @BeforeEach
        void setUp(WireMockRuntimeInfo wireMockRuntimeInfo) {

        String baseUrl = wireMockRuntimeInfo.getHttpBaseUrl();

        identityClient = new IdentityClient(
                new RestTemplate(),
                baseUrl
        );
        }

    @Test
    @DisplayName("U23: Respuesta válida retorna UUID correcto")
    void test1_validResponse_returnsCorrectUUID() {
        String expectedId = "11111111-2222-3333-4444-555555555555";
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"" + expectedId + "\"}")));

        UUID result = identityClient.getAnonymousId("staff_guard");

        assertEquals(UUID.fromString(expectedId), result);
    }

    @Test
    @DisplayName("U24: HTTP 500 del Identity Service lanza IllegalStateException")
    void test2_http500_throwsIllegalStateException() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(serverError()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> identityClient.getAnonymousId("staff_guard"));

        assertTrue(ex.getMessage().contains("Identity service error: HTTP"));
    }

    @Test
    @DisplayName("U25: Respuesta sin anonymousId lanza IllegalStateException")
    void test3_missingAnonymousId_throwsIllegalStateException() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"other_field\": \"value\"}")));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> identityClient.getAnonymousId("staff_guard"));

        assertTrue(ex.getMessage().contains("malformed response"));
    }

    @Test
    @DisplayName("U26: Respuesta null del Identity Service lanza IllegalStateException")
    void test4_nullResponse_throwsIllegalStateException() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("null")));

        assertThrows(IllegalStateException.class,
                () -> identityClient.getAnonymousId("staff_guard"));
    }

    @Test
    @DisplayName("U27: HTTP 404 del Identity Service lanza IllegalStateException")
    void test5_http404_throwsIllegalStateException() {
        stubFor(post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(notFound()));

        assertThrows(IllegalStateException.class,
                () -> identityClient.getAnonymousId("staff_guard"));
    }
}