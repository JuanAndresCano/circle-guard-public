package com.circleguard.auth.integration;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
                    .withUsername("test")
                    .withPassword("test");

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

    @Test
    void test1_validLogin_successfulIntegrationWithIdentityService() throws Exception {
        // [MOCK] 1. Simulamos el Identity-Service
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"anonymousId\": \"11111111-2222-3333-4444-555555555555\"}")));

        // 2. Hacemos login en el Auth-Service
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.anonymousId").value("11111111-2222-3333-4444-555555555555"));
                
        verify(1, postRequestedFor(urlEqualTo("/api/v1/identities/map")));
    }

    @Test
    void test2_invalidLogin_doesNotCallIdentityService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"wrong_user\", \"password\": \"wrong_pass\"}"))
                .andExpect(status().isUnauthorized());

        verify(0, postRequestedFor(urlEqualTo("/api/v1/identities/map")));
    }

    @Test
    void test3_identityServiceDown_returnsInternalServerError() throws Exception {
        stubFor(WireMock.post(urlEqualTo("/api/v1/identities/map"))
                .willReturn(serverError())); // HTTP 500

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"staff_guard\", \"password\": \"password\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void test4_missingBodyParameters_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
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
}