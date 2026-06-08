package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerAndLogin_fullFlow_succeeds() throws Exception {
        // 1) Register
        Map<String, String> reg = Map.of(
                "username", "integrationUser",
                "email", "integration@example.com",
                "password", "pass1234",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        // 2) Login
        Map<String, String> login = Map.of(
                "email", "integration@example.com",
                "password", "pass1234");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        Map<String, String> reg = Map.of(
                "username", "user1",
                "email", "dup@example.com",
                "password", "pass",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        // Second registration with the same email
        Map<String, String> dup = Map.of(
                "username", "user2",
                "email", "dup@example.com",
                "password", "pass",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already in use"));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        Map<String, String> reg = Map.of(
                "username", "userA",
                "email", "usera@example.com",
                "password", "correctpass",
                "country", "PL");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        Map<String, String> login = Map.of(
                "email", "usera@example.com",
                "password", "wrongpass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void refreshToken_withValidRefreshToken_returnsNewAccessToken() throws Exception {
        // Register
        Map<String, String> reg = Map.of(
                "username", "refreshUser",
                "email", "refresh@example.com",
                "password", "pass1234",
                "country", "DE");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        // Login to get tokens
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "refresh@example.com", "password", "pass1234"))))
                .andReturn();

        Map<?, ?> tokens = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        String refreshToken = (String) tokens.get("refreshToken");

        // Use refresh token
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void refreshToken_withJunkToken_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("refreshToken", "not.a.real.token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usersMe_withValidToken_returnsUserInfo() throws Exception {
        // Register + login
        Map<String, String> reg = Map.of(
                "username", "meUser",
                "email", "me@example.com",
                "password", "pass1234",
                "country", "US");
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "me@example.com", "password", "pass1234"))))
                .andReturn();

        Map<?, ?> tokens = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        String accessToken = (String) tokens.get("accessToken");

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.username").value("meUser"));
    }

    @Test
    void register_userIsPersistedInDatabase() throws Exception {
        Map<String, String> reg = Map.of(
                "username", "dbUser",
                "email", "db@example.com",
                "password", "pass1234",
                "country", "FR");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isOk());

        assertThat(userRepository.findByEmail("db@example.com")).isPresent();
        assertThat(userRepository.findByEmail("db@example.com").get().getUsername())
                .isEqualTo("dbUser");
    }
}