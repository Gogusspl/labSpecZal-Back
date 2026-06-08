package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
    }

    @Test
    void register_newUser_savesAndReturnsUser() {
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());

        User saved = buildUser(1L, "newuser", "new@example.com", "encodedPwd", "PL");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = userService.register("newuser", "new@example.com", "password123", "PL");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void register_setsRoleUser() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.register("newuser", "new@example.com", "password123", "PL");

        assertThat(captor.getValue().getRoles()).containsExactly(Role.USER);
    }

    @Test
    void register_encodesPassword() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.register("user", "u@example.com", "rawPassword", "PL");

        String encoded = captor.getValue().getPassword();
        assertThat(encoded).isNotEqualTo("rawPassword");
        assertThat(new BCryptPasswordEncoder().matches("rawPassword", encoded)).isTrue();
    }

    @Test
    void register_setsAllFields() {
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        userService.register("alice", "alice@example.com", "secret", "DE");

        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getCountry()).isEqualTo("DE");
    }

    @Test
    void register_duplicateEmail_throwsRuntimeException() {
        User existing = buildUser(1L, "existing", "dup@example.com", "pwd", "PL");
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() ->
                userService.register("newuser", "dup@example.com", "pass", "PL"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void register_duplicateEmail_doesNotSave() {
        User existing = buildUser(1L, "existing", "dup@example.com", "pwd", "PL");
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        try {
            userService.register("newuser", "dup@example.com", "pass", "PL");
        } catch (RuntimeException ignored) {}

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsUser() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = buildUser(1L, "alice", "alice@example.com", encoder.encode("secret"), "PL");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.login("alice@example.com", "secret");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void login_wrongPassword_returnsEmpty() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = buildUser(1L, "alice", "alice@example.com", encoder.encode("secret"), "PL");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.login("alice@example.com", "wrongPassword");

        assertThat(result).isEmpty();
    }

    @Test
    void login_unknownEmail_returnsEmpty() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.login("unknown@example.com", "pass");

        assertThat(result).isEmpty();
    }

    @Test
    void findByEmail_existingEmail_returnsUser() {
        User user = buildUser(1L, "bob", "bob@example.com", "pwd", "US");
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByEmail("bob@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("bob");
    }

    @Test
    void findByEmail_unknownEmail_returnsEmpty() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void findByUsername_existingUsername_returnsUser() {
        User user = buildUser(1L, "charlie", "charlie@example.com", "pwd", "FR");
        when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByUsername("charlie");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("charlie@example.com");
    }

    @Test
    void findByUsername_unknownUsername_returnsEmpty() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Optional<User> result = userService.findByUsername("ghost");

        assertThat(result).isEmpty();
    }

    private User buildUser(Long id, String username, String email, String password, String country) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(password);
        user.setCountry(country);
        user.setRoles(Set.of(Role.USER));
        return user;
    }
}