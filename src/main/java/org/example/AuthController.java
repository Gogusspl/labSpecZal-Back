package org.example;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }


    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestBody LoginRequest request
    ) {
        Optional<User> userOptional =
                userService.login(request.getEmail(), request.getPassword());

        if (userOptional.isEmpty()) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of("error", "Invalid credentials"));
        }

        User user = userOptional.get();

        Map<String, String> tokens = new HashMap<>();
        tokens.put(
                "accessToken",
                jwtUtil.generateAccessToken(user.getEmail(), user.getRoles())
        );
        tokens.put(
                "refreshToken",
                jwtUtil.generateRefreshToken(user.getEmail())
        );

        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            userService.register(request.getUsername(), request.getEmail(), request.getPassword(), request.getCountry());
            return ResponseEntity.ok(Map.of("message", "User registered successfully"));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(
            @RequestBody Map<String, String> request
    ) {
        String refreshToken = request.get("refreshToken");

        if (refreshToken == null || !jwtUtil.isTokenValid(refreshToken)
                || jwtUtil.isTokenExpired(refreshToken)) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of("error", "Invalid or expired refresh token"));
        }

        String email = jwtUtil.extractEmail(refreshToken);

        Optional<User> userOptional = userService.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of("error", "org.backendApplication.model.User not found"));
        }

        User user = userOptional.get();

        String newAccessToken =
                jwtUtil.generateAccessToken(user.getEmail(), user.getRoles());

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }
}
