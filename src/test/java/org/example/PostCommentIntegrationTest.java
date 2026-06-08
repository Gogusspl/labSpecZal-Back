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
class PostCommentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostViewRepository postViewRepository;

    private String accessToken;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, String> reg = Map.of(
                "username", "postUser",
                "email", "postuser@example.com",
                "password", "pass1234",
                "country", "PL");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reg)));

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "postuser@example.com", "password", "pass1234"))))
                .andReturn();

        Map<?, ?> tokens = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        accessToken = (String) tokens.get("accessToken");
    }

    @Test
    void createPost_authenticated_persistsAndReturns200() throws Exception {
        Map<String, Object> post = Map.of("title", "Integration Test Post", "text", "Content here");

        mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(post)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.title").value("Integration Test Post"))
                .andExpect(jsonPath("$.author").value("postuser@example.com"));
    }

    @Test
    void createPost_setsAuthorFromToken() throws Exception {
        Map<String, Object> post = Map.of("title", "Author Test", "text", "Body");

        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(post)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        assertThat(response.get("author")).isEqualTo("postuser@example.com");
    }

    @Test
    void getAllPosts_returnsPaginatedResult() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    void getAllPosts_afterCreating_includesNewPost() throws Exception {
        // Create a post
        Map<String, Object> post = Map.of("title", "Paginated Post", "text", "Body");
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(post)));

        // Get all posts
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.title == 'Paginated Post')]").exists());
    }

    @Test
    void getPost_authenticated_viewTracked() throws Exception {
        // Create post as postUser
        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "View Test", "text", "Body"))))
                .andReturn();
        Map<?, ?> createdPost = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        long postId = ((Number) createdPost.get("id")).longValue();

        // Register a second user to view the post
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", "viewer", "email", "viewer@example.com",
                        "password", "pass1234", "country", "DE"))));

        MvcResult viewerLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "viewer@example.com", "password", "pass1234"))))
                .andReturn();
        String viewerToken = (String) objectMapper.readValue(
                viewerLogin.getResponse().getContentAsString(), Map.class).get("accessToken");

        // First view increments counter
        mockMvc.perform(get("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views").value(1));

        // Second view by same user counter stays at 1
        mockMvc.perform(get("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views").value(1));
    }

    @Test
    void getPost_authorViewingOwnPost_doesNotIncrementViews() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Own Post", "text", "Body"))))
                .andReturn();
        Map<?, ?> createdPost = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        long postId = ((Number) createdPost.get("id")).longValue();

        // Author views their own post
        mockMvc.perform(get("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views").value(0));
    }

    @Test
    void addComment_authenticated_incrementsPostReplies() throws Exception {
        // Create a post
        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Comment Target", "text", "Body"))))
                .andReturn();
        Map<?, ?> createdPost = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        long postId = ((Number) createdPost.get("id")).longValue();

        // Add a comment
        Map<String, Object> comment = Map.of("postId", postId, "text", "Great post!");
        mockMvc.perform(post("/api/comments")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(comment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Great post!"))
                .andExpect(jsonPath("$.author").value("postuser@example.com"));

        // Verify replies count
        Post updatedPost = postRepository.findById(postId).orElseThrow();
        assertThat(updatedPost.getReplies()).isEqualTo(1);
    }

    @Test
    void getComments_returnsCommentsForPost() throws Exception {
        // Create post
        MvcResult createResult = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Commented Post", "text", "Body"))))
                .andReturn();
        Map<?, ?> createdPost = objectMapper.readValue(createResult.getResponse().getContentAsString(), Map.class);
        long postId = ((Number) createdPost.get("id")).longValue();

        // Add two comments
        mockMvc.perform(post("/api/comments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("postId", postId, "text", "First"))));
        mockMvc.perform(post("/api/comments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("postId", postId, "text", "Second"))));

        mockMvc.perform(get("/api/comments/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}