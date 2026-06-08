package org.example;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @WithMockUser(username = "alice@example.com")
    void me_authenticatedUser_returnsUserDto() throws Exception {
        User user = buildUser(1L, "alice", "alice@example.com", Set.of(Role.USER));
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("USER"));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void me_userNotFoundInDb_returns404() throws Exception {
        when(userService.findByEmail("alice@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isNotFound());
    }


    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void me_adminUser_returnsAdminRole() throws Exception {
        User user = buildUser(2L, "admin", "admin@example.com", Set.of(Role.ADMIN));
        when(userService.findByEmail("admin@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
    }

    @Test
    @WithMockUser(username = "premium@example.com", roles = {"PREMIUM"})
    void me_premiumUser_returnsCorrectDto() throws Exception {
        User user = buildUser(3L, "premium", "premium@example.com", Set.of(Role.PREMIUM));
        when(userService.findByEmail("premium@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.username").value("premium"))
                .andExpect(jsonPath("$.email").value("premium@example.com"));
    }


    private User buildUser(Long id, String username, String email, Set<Role> roles) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("encoded");
        user.setCountry("PL");
        user.setRoles(roles);
        return user;
    }
}