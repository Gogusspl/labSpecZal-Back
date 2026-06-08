package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void login_validCredentials_returns200WithTokens() throws Exception {
        User user = buildUser("alice@example.com", Set.of(Role.USER));
        when(userService.login("alice@example.com", "secret"))
                .thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(anyString(), any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(anyString())).thenReturn("refresh-token");

        Map<String, String> body = Map.of("email", "alice@example.com", "password", "secret");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        when(userService.login(anyString(), anyString())).thenReturn(Optional.empty());

        Map<String, String> body = Map.of("email", "bad@example.com", "password", "wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void register_newUser_returns200WithMessage() throws Exception {
        when(userService.register(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(buildUser("new@example.com", Set.of(Role.USER)));

        Map<String, String> body = Map.of(
                "username", "newuser",
                "email", "new@example.com",
                "password", "pass123",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully"));
    }

    @Test
    void register_duplicateEmail_returns400WithError() throws Exception {
        when(userService.register(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Email already in use"));

        Map<String, String> body = Map.of(
                "username", "user",
                "email", "dup@example.com",
                "password", "pass",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already in use"));
    }

    @Test
    void refresh_validToken_returns200WithNewAccessToken() throws Exception {
        User user = buildUser("alice@example.com", Set.of(Role.USER));

        when(jwtUtil.isTokenValid("valid-refresh")).thenReturn(true);
        when(jwtUtil.isTokenExpired("valid-refresh")).thenReturn(false);
        when(jwtUtil.extractEmail("valid-refresh")).thenReturn("alice@example.com");
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(anyString(), any())).thenReturn("new-access-token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "valid-refresh"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        when(jwtUtil.isTokenValid("bad-token")).thenReturn(false);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "bad-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));
    }

    @Test
    void refresh_expiredToken_returns401() throws Exception {
        when(jwtUtil.isTokenValid("expired")).thenReturn(true);
        when(jwtUtil.isTokenExpired("expired")).thenReturn(true);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "expired"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid or expired refresh token"));
    }

    @Test
    void refresh_missingRefreshTokenKey_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_userNotFound_returns401() throws Exception {
        when(jwtUtil.isTokenValid("valid")).thenReturn(true);
        when(jwtUtil.isTokenExpired("valid")).thenReturn(false);
        when(jwtUtil.extractEmail("valid")).thenReturn("ghost@example.com");
        when(userService.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "valid"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    private User buildUser(String email, Set<Role> roles) {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setEmail(email);
        user.setPassword("encoded");
        user.setCountry("PL");
        user.setRoles(roles);
        return user;
    }
}