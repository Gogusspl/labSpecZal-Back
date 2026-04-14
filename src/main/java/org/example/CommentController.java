package org.example;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "http://localhost:4200")
public class CommentController {

    private final CommentRepository repository;
    private final PostRepository postRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public CommentController(CommentRepository repository,
                             PostRepository postRepository,
                             SimpMessagingTemplate messagingTemplate) {
        this.repository = repository;
        this.postRepository = postRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/{postId}")
    public List<Comment> getByPost(@PathVariable Long postId) {
        return repository.findByPostIdOrderByCreatedAtDesc(postId);
    }

    @PostMapping
    public Comment addComment(@RequestBody Comment comment) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        comment.setAuthor(username);

        Comment saved = repository.save(comment);

        postRepository.findById(comment.getPostId()).ifPresent(post -> {
            post.setReplies(post.getReplies() + 1);
            postRepository.save(post);
        });

        messagingTemplate.convertAndSend(
                "/topic/comments/" + comment.getPostId(),
                saved
        );
        
        messagingTemplate.convertAndSend(
                "/topic/posts/" + comment.getPostId(),
                "NEW_COMMENT"
        );

        return saved;
    }
}