package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommentRepository commentRepository;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private JwtUtil jwtUtil;


    @Test
    void getByPost_returnsListOfComments() throws Exception {
        Comment c1 = buildComment(1L, 10L, "user1", "First comment");
        Comment c2 = buildComment(2L, 10L, "user2", "Second comment");
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(10L)).thenReturn(List.of(c1, c2));

        mockMvc.perform(get("/api/comments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].author").value("user1"))
                .andExpect(jsonPath("$[1].author").value("user2"));
    }

    @Test
    void getByPost_noComments_returnsEmptyList() throws Exception {
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/comments/99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "alice@example.com")
    void addComment_authenticatedUser_savesAndReturnsComment() throws Exception {
        Comment input = buildComment(null, 10L, null, "Nice post!");
        Comment saved = buildComment(1L, 10L, "alice@example.com", "Nice post!");

        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(postRepository.findById(10L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("alice@example.com"))
                .andExpect(jsonPath("$.text").value("Nice post!"));
    }

    @Test
    @WithMockUser(username = "bob@example.com")
    void addComment_incrementsPostReplies() throws Exception {
        Comment input = buildComment(null, 5L, null, "Hello");
        Comment saved = buildComment(1L, 5L, "bob@example.com", "Hello");

        when(commentRepository.save(any(Comment.class))).thenReturn(saved);

        Post post = buildPost(5L, "bob2@example.com", 3);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenReturn(post);

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        verify(postRepository).save(argThat(p -> p.getReplies() == 4));
    }

    @Test
    @WithMockUser(username = "carol@example.com")
    void addComment_sendsWebSocketMessages() throws Exception {
        Comment input = buildComment(null, 7L, null, "WS test");
        Comment saved = buildComment(1L, 7L, "carol@example.com", "WS test");

        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(postRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk());

        verify(messagingTemplate).convertAndSend(eq("/topic/comments/7"), eq(saved));
        verify(messagingTemplate).convertAndSend(eq("/topic/posts/7"), eq("NEW_COMMENT"));
    }

    @Test
    @WithMockUser(username = "dave@example.com")
    void addComment_setsAuthorFromAuthentication() throws Exception {
        Comment input = buildComment(null, 1L, "ignored-author", "text");

        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setPostId(1L);
            return c;
        });
        when(postRepository.findById(any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("dave@example.com"));
    }

    private Comment buildComment(Long id, Long postId, String author, String text) {
        Comment c = new Comment();
        if (id != null) {
            try {
                var field = Comment.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(c, id);
            } catch (Exception ignored) {}
        }
        c.setPostId(postId);
        c.setAuthor(author);
        c.setText(text);
        return c;
    }

    private Post buildPost(Long id, String author, int replies) {
        Post p = new Post();
        p.setId(id);
        p.setAuthor(author);
        p.setReplies(replies);
        return p;
    }
}