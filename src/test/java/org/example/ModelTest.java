package org.example;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void comment_defaultCreatedAt_isNotNull() {
        Comment comment = new Comment();
        assertThat(comment.getCreatedAt()).isNotNull();
        assertThat(comment.getCreatedAt()).isBefore(LocalDateTime.now().plusSeconds(1));
    }

    @Test
    void comment_settersAndGetters_workCorrectly() {
        Comment comment = new Comment();
        comment.setPostId(42L);
        comment.setAuthor("alice");
        comment.setText("Hello World");
        comment.setCode("int x = 1;");
        comment.setCodeLanguage("java");

        assertThat(comment.getPostId()).isEqualTo(42L);
        assertThat(comment.getAuthor()).isEqualTo("alice");
        assertThat(comment.getText()).isEqualTo("Hello World");
        assertThat(comment.getCode()).isEqualTo("int x = 1;");
        assertThat(comment.getCodeLanguage()).isEqualTo("java");
        assertThat(comment.getId()).isNull();
    }

    @Test
    void post_defaultValues_areCorrect() {
        Post post = new Post();
        assertThat(post.getReplies()).isEqualTo(0);
        assertThat(post.getViews()).isEqualTo(0);
        assertThat(post.getCreatedAt()).isNotNull();
    }

    @Test
    void post_settersAndGetters_workCorrectly() {
        Post post = new Post();
        LocalDateTime now = LocalDateTime.now();

        post.setId(1L);
        post.setTitle("Test Title");
        post.setText("Test text");
        post.setCode("code snippet");
        post.setLanguage("EN");
        post.setCodeLanguage("java");
        post.setAuthor("bob");
        post.setCreatedAt(now);
        post.setReplies(5);
        post.setViews(100);
        post.setTags(List.of("java", "spring"));

        assertThat(post.getId()).isEqualTo(1L);
        assertThat(post.getTitle()).isEqualTo("Test Title");
        assertThat(post.getText()).isEqualTo("Test text");
        assertThat(post.getCode()).isEqualTo("code snippet");
        assertThat(post.getLanguage()).isEqualTo("EN");
        assertThat(post.getCodeLanguage()).isEqualTo("java");
        assertThat(post.getAuthor()).isEqualTo("bob");
        assertThat(post.getCreatedAt()).isEqualTo(now);
        assertThat(post.getReplies()).isEqualTo(5);
        assertThat(post.getViews()).isEqualTo(100);
        assertThat(post.getTags()).containsExactly("java", "spring");
    }

    @Test
    void postView_defaultCreatedAt_isNotNull() {
        PostView view = new PostView();
        assertThat(view.getCreatedAt()).isNotNull();
    }

    @Test
    void postView_settersAndGetters_workCorrectly() {
        PostView view = new PostView();
        LocalDateTime now = LocalDateTime.now();

        view.setId(10L);
        view.setPostId(5L);
        view.setUserEmail("viewer@example.com");
        view.setCreatedAt(now);

        assertThat(view.getId()).isEqualTo(10L);
        assertThat(view.getPostId()).isEqualTo(5L);
        assertThat(view.getUserEmail()).isEqualTo("viewer@example.com");
        assertThat(view.getCreatedAt()).isEqualTo(now);
    }

    @Test
    void user_settersAndGetters_workCorrectly() {
        User user = new User();
        user.setId(1L);
        user.setUsername("charlie");
        user.setEmail("charlie@example.com");
        user.setPassword("encoded");
        user.setCountry("PL");
        user.setRoles(Set.of(Role.USER, Role.ADMIN));

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getUsername()).isEqualTo("charlie");
        assertThat(user.getEmail()).isEqualTo("charlie@example.com");
        assertThat(user.getPassword()).isEqualTo("encoded");
        assertThat(user.getCountry()).isEqualTo("PL");
        assertThat(user.getRoles()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN);
    }


    @Test
    void userDto_recordFieldsAccessible() {
        UserDto dto = new UserDto(1L, "alice", "alice@example.com", Set.of(Role.USER));

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.username()).isEqualTo("alice");
        assertThat(dto.email()).isEqualTo("alice@example.com");
        assertThat(dto.roles()).containsExactly(Role.USER);
    }

    @Test
    void userDto_equality_sameValues() {
        UserDto dto1 = new UserDto(1L, "alice", "alice@example.com", Set.of(Role.USER));
        UserDto dto2 = new UserDto(1L, "alice", "alice@example.com", Set.of(Role.USER));
        assertThat(dto1).isEqualTo(dto2);
    }

    @Test
    void loginRequest_settersAndGetters_workCorrectly() {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("secret");

        assertThat(req.getEmail()).isEqualTo("test@example.com");
        assertThat(req.getPassword()).isEqualTo("secret");
    }

    @Test
    void registerRequest_settersAndGetters_workCorrectly() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newUser");
        req.setEmail("new@example.com");
        req.setPassword("pass");
        req.setCountry("PL");

        assertThat(req.getUsername()).isEqualTo("newUser");
        assertThat(req.getEmail()).isEqualTo("new@example.com");
        assertThat(req.getPassword()).isEqualTo("pass");
        assertThat(req.getCountry()).isEqualTo("PL");
    }

    @Test
    void role_enumValues_areCorrect() {
        assertThat(Role.values()).containsExactlyInAnyOrder(Role.USER, Role.ADMIN, Role.PREMIUM);
    }

    @Test
    void role_valueOf_workForAllRoles() {
        assertThat(Role.valueOf("USER")).isEqualTo(Role.USER);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
        assertThat(Role.valueOf("PREMIUM")).isEqualTo(Role.PREMIUM);
    }
}