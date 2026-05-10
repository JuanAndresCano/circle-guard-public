package com.circleguard.auth;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.*;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("e2e")
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@WireMockTest(httpPort = 8084)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2EAuthFlowTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("circleguardauth_e2e")
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

    // Helper para extraer campo JSON del response sin Jackson en el test
    private String extractJsonField(String json, String field) {
        return json.split("\"" + field + "\":\"")[1].split("\"")[0];
    }

    // --- Flujos negativos (se mantienen, mejorados) ---

    @Test
    @Order(1)
    @DisplayName("E2E-01: Login con credenciales inválidas retorna 401")
    void e2e_loginWithInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"usuario_falso\", \"password\": \"clave_incorrecta\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @Order(2)
    @DisplayName("E2E-02: Login con payload vacío retorna 401")
    void e2e_loginWithEmptyPayload_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(3)
    @DisplayName("E2E-03: Endpoint protegido sin token retorna 401/403")
    void e2e_accessProtectedEndpointWithoutToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
                .andExpect(status().is4xxClientError());
    }

    // --- Flujos positivos nuevos ---

    @Test
    @Order(4)
    @DisplayName("E2E-04: Flujo completo — Login → obtener JWT → acceder a QR generate")
    void e2e_fullLoginFlow_loginThenAccessProtectedQrEndpoint() throws Exception {
        // Usamos WireMock en puerto 8084 para el Identity Service
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"e2e00001-0000-0000-0000-000000000001\"}")));

        // Step 1: Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "token");
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length, "Token debe ser JWT válido");

        // Step 2: Usar el JWT para acceder a endpoint protegido
        mockMvc.perform(get("/api/v1/auth/qr/generate")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrToken").exists())
                .andExpect(jsonPath("$.expiresIn").value("60"));
    }

    @Test
    @Order(5)
    @DisplayName("E2E-05: Flujo completo — Login → JWT inválido modificado → QR generate rechazado")
    void e2e_tamperedToken_isRejectedOnProtectedEndpoint() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"e2e00002-0000-0000-0000-000000000002\"}")));

        // Step 1: Login legítimo
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "token");

        // Step 2: Manipular el token (cambiar último caracter)
        String tamperedToken = token.substring(0, token.length() - 4) + "XXXX";

        // Step 3: Acceder al endpoint con token manipulado — debe fallar
        mockMvc.perform(get("/api/v1/auth/qr/generate")
                .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(6)
    @DisplayName("E2E-06: Flujo visitor — Handoff genera token utilizable")
    void e2e_visitorHandoffFlow_generateAndVerifyVisitorToken() throws Exception {
        String anonymousId = "visitor1-0000-0000-0000-000000000001";

        MvcResult handoffResult = mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.handoffPayload").value(containsString("HANDOFF_TOKEN:" + anonymousId)))
                .andReturn();

        String visitorToken = extractJsonField(
                handoffResult.getResponse().getContentAsString(), "token");
        assertEquals(3, visitorToken.split("\\.").length, "Visitor token debe ser JWT válido");
    }

    @Test
    @Order(7)
    @DisplayName("E2E-07: Flujo de permisos — Usuario con HEALTH_CENTER aparece en consulta de permisos")
    void e2e_permissionsFlow_healthUserHasCorrectPermissions() throws Exception {
        // Verificar que health_user tiene permiso identity:lookup
        mockMvc.perform(get("/api/v1/users/permissions/identity:lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("health_user")));

        // Verificar que staff_guard NO tiene permiso identity:lookup
        mockMvc.perform(get("/api/v1/users/permissions/identity:lookup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", not(hasItem("staff_guard"))));
    }

    @Test
    @Order(8)
    @DisplayName("E2E-08: Flujo completo de health_user — Login → JWT → consulta de usuarios por permiso")
    void e2e_healthUserFullFlow_loginAndQueryPermissions() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"e2e00003-0000-0000-0000-000000000003\"}")));

        // Step 1: Login como health_user
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"health_user\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value("e2e00003-0000-0000-0000-000000000003"))
                .andReturn();

        String token = extractJsonField(loginResult.getResponse().getContentAsString(), "token");

        // Step 2: Con el JWT, consultar usuarios con permiso alert:receive_priority
        mockMvc.perform(get("/api/v1/users/permissions/alert:receive_priority")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("health_user")));
    }

    @Test
    @Order(9)
    @DisplayName("E2E-09: Identity Service caído no rompe el sistema — retorna 500 controlado")
    void e2e_identityServiceDown_systemHandlesGracefully() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(serverError()));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message").value(containsString("Internal server error")));
    }

    @Test
    @Order(10)
    @DisplayName("E2E-10: super_admin tiene acceso a todos los permisos del sistema")
    void e2e_superAdmin_hasAccessToAllRoles() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"admin000-0000-0000-0000-000000000001\"}")));

        // Login como super_admin
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"super_admin\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());

        // super_admin aparece en consultas de roles HEALTH_CENTER y GATE_STAFF
        mockMvc.perform(get("/api/v1/users/permissions/gate:scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].username", hasItem("super_admin")));
    }
}