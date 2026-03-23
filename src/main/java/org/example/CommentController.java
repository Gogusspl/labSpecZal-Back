package org.example;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@CrossOrigin(origins = "http://localhost:4200")
public class CommentController {

    private final CommentRepository repository;
    private final PostRepository postRepository;

    public CommentController(CommentRepository repository, PostRepository postRepository) {
        this.repository = repository;
        this.postRepository = postRepository;
    }

    // 🔹 GET komentarzy do posta
    @GetMapping("/{postId}")
    public List<Comment> getByPost(@PathVariable Long postId) {
        return repository.findByPostIdOrderByCreatedAtDesc(postId);
    }

    // 🔹 DODAWANIE komentarza
    @PostMapping
    public Comment addComment(@RequestBody Comment comment) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        comment.setAuthor(username);

        Comment saved = repository.save(comment);

        // 🔥 zwiększamy licznik replies w poście
        postRepository.findById(comment.getPostId()).ifPresent(post -> {
            post.setReplies(post.getReplies() + 1);
            postRepository.save(post);
        });

        return saved;
    }
}