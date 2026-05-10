package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.service.JwtTokenService;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.security.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
public class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    // --- Test original ---

    @Test
    @DisplayName("U16: Login válido retorna 200 con token, anonymousId y type=Bearer")
    void test1_shouldLoginSuccessfullyAndReturnAnonymizedToken() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        String token = "mock-jwt-token";

        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId("testuser")).thenReturn(anonymousId);
        Mockito.when(jwtService.generateToken(eq(anonymousId), any(Authentication.class)))
                .thenReturn(token);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

    // --- Tests nuevos ---

    @Test
    @DisplayName("U17: Credenciales incorrectas retorna 401")
    void test2_invalidCredentials_returns401() throws Exception {
        Mockito.when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"bad_user\", \"password\": \"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("U18: Identity Service caído retorna 500 con mensaje controlado")
    void test3_identityServiceDown_returns500() throws Exception {
        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId(any()))
                .thenThrow(new IllegalStateException("Identity service unreachable"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password123\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("U19: Login válido no expone password en la respuesta")
    void test4_successfulLogin_doesNotExposePassword() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = Mockito.mock(Authentication.class);
        Mockito.when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId("testuser")).thenReturn(anonymousId);
        Mockito.when(jwtService.generateToken(eq(anonymousId), any(Authentication.class)))
                .thenReturn("some-token");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.username").doesNotExist());
    }

    @Test
    @DisplayName("U20: Visitor handoff con anonymousId válido retorna token y handoffPayload")
    void test5_visitorHandoff_validId_returnsTokenAndPayload() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Mockito.when(jwtService.generateToken(eq(anonymousId), any(Authentication.class)))
                .thenReturn("visitor-jwt-token");

        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"anonymousId\": \"" + anonymousId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("visitor-jwt-token"))
                .andExpect(jsonPath("$.handoffPayload").exists());
    }

    @Test
    @DisplayName("U21: Visitor handoff sin anonymousId retorna 400")
    void test6_visitorHandoff_missingId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/visitor/handoff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("U22: Login válido con permisos retorna token que incluye authorities del usuario")
    void test7_successfulLogin_withRoles_tokenIsGenerated() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "testuser", null,
                List.of(new SimpleGrantedAuthority("ROLE_GATE_STAFF"),
                        new SimpleGrantedAuthority("gate:scan"))
        );
        Mockito.when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        Mockito.when(identityClient.getAnonymousId("testuser")).thenReturn(anonymousId);
        Mockito.when(jwtService.generateToken(eq(anonymousId), any(Authentication.class)))
                .thenReturn("token-with-roles");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"testuser\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-with-roles"));
    }
}