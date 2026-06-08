package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PostRepository postRepository;

    @MockBean
    private PostViewRepository postViewRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @MockBean
    private JwtUtil jwtUtil;

    @Test
    void getAllPosts_defaultPaging_returnsPagedResult() throws Exception {
        Post p1 = buildPost(1L, "alice", 0);
        Post p2 = buildPost(2L, "bob", 0);
        Page<Post> page = new PageImpl<>(List.of(p1, p2));
        when(postRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    void getAllPosts_customPaging_passesParamsToRepository() throws Exception {
        when(postRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        mockMvc.perform(get("/api/posts?page=2&size=5"))
                .andExpect(status().isOk());

        verify(postRepository).findAll(PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = {"USER"})
    void createPost_authenticatedUser_savesPostAndReturns200() throws Exception {
        Post input = buildPost(null, null, 0);
        input.setTitle("New Post");

        Post saved = buildPost(1L, "alice@example.com", 0);
        saved.setTitle("New Post");

        when(postRepository.save(any(Post.class))).thenReturn(saved);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("alice@example.com"))
                .andExpect(jsonPath("$.title").value("New Post"));
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = {"USER"})
    void createPost_sendsWebSocketMessage() throws Exception {
        Post saved = buildPost(1L, "alice@example.com", 0);
        when(postRepository.save(any(Post.class))).thenReturn(saved);

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildPost(null, null, 0))))
                .andExpect(status().isOk());

        verify(messagingTemplate).convertAndSend(eq("/topic/posts"), eq(saved));
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = {"USER"})
    void createPost_setsAuthorFromAuth() throws Exception {
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildPost(null, "different-author", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").value("alice@example.com"));
    }

    @Test
    @WithMockUser(username = "viewer@example.com")
    void getPost_newViewer_incrementsViewsAndSavesView() throws Exception {
        Post post = buildPost(1L, "author@example.com", 5);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postViewRepository.existsByPostIdAndUserEmail(1L, "viewer@example.com")).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenReturn(post);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());

        verify(postViewRepository).save(any(PostView.class));
        verify(postRepository).save(argThat(p -> p.getViews() == 6));
    }

    @Test
    @WithMockUser(username = "viewer@example.com")
    void getPost_alreadyViewed_doesNotIncrementViews() throws Exception {
        Post post = buildPost(1L, "author@example.com", 10);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postViewRepository.existsByPostIdAndUserEmail(1L, "viewer@example.com")).thenReturn(true);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());

        verify(postViewRepository, never()).save(any());
        verify(postRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "author@example.com")
    void getPost_authorViewingOwnPost_doesNotIncrementViews() throws Exception {
        Post post = buildPost(1L, "author@example.com", 3);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());

        verify(postViewRepository, never()).existsByPostIdAndUserEmail(any(), any());
        verify(postRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "viewer@example.com")
    void getPost_newView_sendsWebSocketViewMessage() throws Exception {
        Post post = buildPost(1L, "other@example.com", 0);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(postViewRepository.existsByPostIdAndUserEmail(1L, "viewer@example.com")).thenReturn(false);
        when(postRepository.save(any())).thenReturn(post);

        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());

        verify(messagingTemplate).convertAndSend(eq("/topic/posts/1"), eq("VIEW"));
    }

    private Post buildPost(Long id, String author, int views) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Test Post");
        post.setAuthor(author);
        post.setViews(views);
        post.setReplies(0);
        return post;
    }
}