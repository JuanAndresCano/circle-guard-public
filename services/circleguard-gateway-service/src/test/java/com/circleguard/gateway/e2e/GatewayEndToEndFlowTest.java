package com.circleguard.gateway.e2e;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * System-Wide End-to-End Tests for Circle Guard.
 *
 * Este suite valida la integración completa del ecosistema:
 * - Gateway
 * - Auth Service
 * - Identity Service
 * - Survey Service
 * - Encounter Service
 *
 * Requiere docker-compose completamente levantado.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GatewayEndToEndFlowTest {

    private static final String GATEWAY_URL =
            System.getProperty("gateway.url", "http://localhost:8087");

    private static final String HEALTH_ENDPOINT = "/actuator/health";

    private static final int MAX_RETRIES = 15;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);

    private static String jwtToken;
    private static String anonymousId;

    @BeforeAll
    static void setup() throws InterruptedException {
        RestAssured.baseURI = GATEWAY_URL;

        waitForGatewayAvailability();
    }

    @Test
    @Order(1)
    void flow1_Authentication_ReturnsValidJwt() {

        Map<String, String> loginPayload = Map.of(
                "username", "super_admin",
                "password", "password"
        );

        Response response = postRequest(
                "/api/v1/auth/login",
                loginPayload,
                null,
                null
        );

        response.prettyPrint();

        assertEquals(200, response.getStatusCode());

        jwtToken = response.jsonPath().getString("token");
        anonymousId = response.jsonPath().getString("anonymousId");

        assertNotNull(jwtToken);
        assertNotNull(anonymousId);
    }

    @Test
    @Order(2)
    void flow2_IdentityProfile_RegistrationAndQuery() {
        Map<String, String> payload = Map.of(
                "realIdentity", "student@example.edu"
        );

        postRequest(
                "/api/v1/identities/map",
                payload,
                jwtToken,
                null
        ).then()
                .statusCode(anyOf(is(200), is(201)));
    }

    @Test
    @Order(3)
    void flow3_HealthSurvey_SubmissionAndAsyncNotification() {
        String currentAnonymousId = getAnonymousId();

        Map<String, Object> payload = Map.of(
                "anonymousId", currentAnonymousId,
                "symptoms", new String[]{"FEVER", "COUGH"},
                "temperature", 38.5,
                "contactWithInfected", true
        );

        postRequest(
                "/api/v1/surveys",
                payload,
                jwtToken,
                currentAnonymousId
        ).then()
                .statusCode(anyOf(
                        is(200),
                        is(201),
                        is(202)
                ));
    }

    @Test
    @Order(4)
    void flow4_PromotionAndNetworking_Neo4jGraph() {
        String sourceId = getAnonymousId();

        Map<String, Object> payload = Map.of(
                "sourceId", sourceId,
                "targetId", UUID.randomUUID().toString(),
                "locationId", "building-123"
        );

        postRequest(
                "/api/v1/encounters/report",
                payload,
                jwtToken,
                null
        ).then()
                .statusCode(anyOf(is(200), is(201)));
    }

    @Test
    @Order(5)
    void flow5_GatewaySecurity_AccessDeniedWithoutToken() {

        Map<String, String> invalidTokenPayload = Map.of(
                "token", "esto_no_es_un_jwt_y_es_invalido"
        );

        postRequest(
                "/api/v1/gate/validate",
                invalidTokenPayload,
                null,
                null
        ).then()
                .statusCode(401);

        postRequest(
                "/api/v1/gate/validate",
                Map.of(),
                null,
                null
        ).then()
                .statusCode(401);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private static void waitForGatewayAvailability() throws InterruptedException {

        System.out.printf(
                "⏳ Esperando disponibilidad del Gateway en %s%n",
                GATEWAY_URL
        );

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {

            try {
                given()
                        .baseUri(GATEWAY_URL)
                .when()
                        .get(HEALTH_ENDPOINT)
                .then()
                        .statusCode(anyOf(
                                is(200),
                                is(401),
                                is(404)
                        ));

                System.out.println("Gateway disponible.");
                return;

            } catch (Exception exception) {

                System.out.printf(
                        "Gateway aún no disponible. Reintento %d/%d%n",
                        attempt,
                        MAX_RETRIES
                );

                Thread.sleep(RETRY_DELAY.toMillis());
            }
        }

        System.err.println(
                "Timeout esperando disponibilidad del Gateway."
        );
    }

    private static Response postRequest(
            String endpoint,
            Object payload,
            String token,
            String anonymousHeader
    ) {

        var request = given()
                .baseUri(GATEWAY_URL)
                .contentType(ContentType.JSON)
                .log().ifValidationFails();

        if (token != null && !token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }

        if (anonymousHeader != null && !anonymousHeader.isBlank()) {
            request.header("X-Anonymous-Id", anonymousHeader);
        }

        return request
                .body(payload)
        .when()
                .post(endpoint);
    }

    private static String getAnonymousId() {
        return anonymousId != null
                ? anonymousId
                : UUID.randomUUID().toString();
    }
}