package com.circleguard.auth.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WireMockTest(httpPort = 8083)
@Testcontainers
public class LoginIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("circleguardauth")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                    .withStartupTimeout(Duration.ofSeconds(60));

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    // --- Tests originales ---

    @Test
    @DisplayName("I1: Login válido retorna JWT y anonymousId correcto")
    void test1_validLogin_successfulIntegrationWithIdentityService() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"11111111-2222-3333-4444-555555555555\"}")));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.anonymousId").value("11111111-2222-3333-4444-555555555555"));

        verify(1, postRequestedFor(urlEqualTo("/api/v1/identities/map")));
    }

    @Test
    @DisplayName("I2: Login inválido no llama al Identity Service")
    void test2_invalidLogin_doesNotCallIdentityService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"wrong_user\", \"password\": \"wrong_pass\"}"))
                .andExpect(status().isUnauthorized());

        verify(0, postRequestedFor(urlEqualTo("/api/v1/identities/map")));
    }

    @Test
    @DisplayName("I3: Identity Service caído retorna 500")
    void test3_identityServiceDown_returnsInternalServerError() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(serverError()));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("I4: Body vacío retorna 401")
    void test4_missingBodyParameters_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("I5: Identity Service retorna respuesta malformada, auth falla con 500")
    void test5_identityServiceReturnsMalformedData_authFailsGracefully() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"unexpected_key\": \"hello_world\"}")));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isInternalServerError());
    }

    // --- Tests nuevos ---

    @Test
    @DisplayName("I6: Login de health_user retorna JWT con permisos de HEALTH_CENTER")
    void test6_healthUserLogin_returnsTokenWithHealthCenterPermissions() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}")));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"health_user\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.anonymousId").value("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
    }

    @Test
    @DisplayName("I7: Visitor handoff con anonymousId válido retorna token y handoffPayload")
    void test7_visitorHandoff_validAnonymousId_returnsTokenAndPayload() throws Exception {
        String anonymousId = "12345678-1234-1234-1234-123456789012";

        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.handoffPayload").value(
                        containsString("HANDOFF_TOKEN:" + anonymousId)));
    }

    @Test
    @DisplayName("I8: Visitor handoff sin anonymousId retorna 400")
    void test8_visitorHandoff_missingAnonymousId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("I9: GET /users/permissions retorna usuarios con permiso identity:lookup")
    void test9_getUsersByPermission_returnsHealthCenterUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users/permissions/identity:lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[*].username", hasItem("health_user")));
    }

    @Test
    @DisplayName("I10: GET /users/permissions para permiso inexistente retorna lista vacía")
    void test10_getUsersByPermission_unknownPermission_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/users/permissions/permiso:inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("I11: Token JWT obtenido en login puede usarse para acceder a endpoint protegido")
    void test11_tokenFromLogin_canAccessProtectedEndpoint() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"ffffffff-ffff-ffff-ffff-ffffffffffff\"}")));

        // 1. Login para obtener token
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        // Extraemos el token del JSON response manualmente
        String token = responseBody.split("\"token\":\"")[1].split("\"")[0];

        // 2. Usar token para acceder a QR generate (endpoint protegido)
        mockMvc.perform(get("/api/v1/auth/qr/generate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").exists())
                .andExpect(jsonPath("$.expiresIn").value("60"));
    }
}