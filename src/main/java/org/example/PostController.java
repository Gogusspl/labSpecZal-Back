package org.example;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostRepository repository;
    private final PostViewRepository postViewRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PostController(PostRepository repository, PostViewRepository postViewRepository, SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.postViewRepository = postViewRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public Page<Post> getAllPosts(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        return repository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PREMIUM') or hasAnyAuthority('USER', 'ADMIN', 'PREMIUM')")
    public Post createPost(@RequestBody Post post) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        post.setAuthor(username);

        Post saved = repository.save(post);

        messagingTemplate.convertAndSend(
                "/topic/posts",
                saved
        );

        return saved;
    }
    @GetMapping("/{id}")
    public Post getPost(@PathVariable Long id) {

        Post post = repository.findById(id).orElseThrow();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {

            String email = auth.getName();

            if (!email.equals(post.getAuthor())) {

                boolean alreadyViewed =
                        postViewRepository.existsByPostIdAndUserEmail(id, email);

                if (!alreadyViewed) {

                    PostView view = new PostView();
                    view.setPostId(id);
                    view.setUserEmail(email);
                    postViewRepository.save(view);

                    post.setViews(post.getViews() + 1);
                    repository.save(post);

                    messagingTemplate.convertAndSend(
                            "/topic/posts/" + id,
                            "VIEW"
                    );
                }
            }
        }

        return post;
    }
}