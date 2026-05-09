package com.circleguard.auth;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.Assertions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class E2EAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @Order(1)
    @DisplayName("E2E-01: Login con credenciales inválidas debe retornar 401")
    void e2e_loginWithInvalidCredentials_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"usuario_falso\", \"password\": \"clave_incorrecta\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(2)
    @DisplayName("E2E-02: Login con payload vacío debe retornar 4xx")
    void e2e_loginWithEmptyPayload_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(3)
    @DisplayName("E2E-03: Endpoint protegido sin token debe retornar 401/403")
    void e2e_accessProtectedEndpointWithoutToken_returnsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/auth/qr/generate"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(4)
    @DisplayName("E2E-04: Login con username nulo debe ser rechazado")
    void e2e_loginWithNullUsername_isRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": null, \"password\": \"algo\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @Order(5)
    @DisplayName("E2E-05: Visitor handoff con payload malformado debe retornar 4xx")
    void e2e_visitorHandoffWithMalformedPayload_returns4xx() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"campo_inexistente\": \"valor\"}"))
            .andExpect(status().is4xxClientError());
    }
}