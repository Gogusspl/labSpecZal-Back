package org.example;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final WebClient webClient;

    public ChatController(WebClient webClient) {
        this.webClient = webClient;
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody Map<String, String> req) {

        String message = req.get("message");

        Map response = webClient.post()
                .uri("http://localhost:8000/chat")
                .bodyValue(Map.of("message", message))
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return Map.of(
                "response",
                response.get("response").toString()
        );
    }
}