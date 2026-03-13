package org.example;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(columnDefinition = "TEXT")
    private String code;

    private String language;

    private String codeLanguage;

    private String author;

    private LocalDateTime createdAt = LocalDateTime.now();

    private int replies = 0;
    private int views = 0;

    @ElementCollection
    private List<String> tags;

    // --- Gettery i settery ---
    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getText() { return text; }

    public void setText(String text) { this.text = text; }

    public String getCode() { return code; }

    public void setCode(String code) { this.code = code; }

    public String getLanguage() { return language; }

    public void setLanguage(String language) { this.language = language; }

    public String getCodeLanguage() { return codeLanguage; }

    public void setCodeLanguage(String codeLanguage) { this.codeLanguage = codeLanguage; }

    public String getAuthor() { return author; }

    public void setAuthor(String author) { this.author = author; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getReplies() { return replies; }

    public void setReplies(int replies) { this.replies = replies; }

    public int getViews() { return views; }

    public void setViews(int views) { this.views = views; }

    public List<String> getTags() { return tags; }

    public void setTags(List<String> tags) { this.tags = tags; }
}
