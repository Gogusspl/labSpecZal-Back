package org.example;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    private static final String SECRET =
            "test-secret-key-for-unit-tests-that-is-long-enough-for-hmac-sha512";
    private static final long ACCESS_EXP  = 3_600_000L;
    private static final long REFRESH_EXP = 86_400_000L;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_EXP, REFRESH_EXP);
    }

    @Test
    void generateAccessToken_returnsNonNullToken() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateAccessToken_containsThreeJwtParts() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void generateRefreshToken_returnsNonNullToken() {
        String token = jwtUtil.generateRefreshToken("user@example.com");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void generateRefreshToken_containsThreeJwtParts() {
        String token = jwtUtil.generateRefreshToken("user@example.com");
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void extractEmail_fromAccessToken_returnsCorrectEmail() {
        String email = "user@example.com";
        String token = jwtUtil.generateAccessToken(email, Set.of(Role.USER));
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void extractEmail_fromRefreshToken_returnsCorrectEmail() {
        String email = "admin@example.com";
        String token = jwtUtil.generateRefreshToken(email);
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void extractRoles_singleRole_returnsCorrectRole() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        Set<Role> roles = jwtUtil.extractRoles(token);
        assertThat(roles).containsExactlyInAnyOrder(Role.USER);
    }

    @Test
    void extractRoles_multipleRoles_returnsAllRoles() {
        Set<Role> inputRoles = Set.of(Role.USER, Role.ADMIN, Role.PREMIUM);
        String token = jwtUtil.generateAccessToken("admin@example.com", inputRoles);
        Set<Role> roles = jwtUtil.extractRoles(token);
        assertThat(roles).containsExactlyInAnyOrderElementsOf(inputRoles);
    }

    @Test
    void extractRoles_refreshTokenHasNoRoles_returnsEmptySet() {
        String token = jwtUtil.generateRefreshToken("user@example.com");
        Set<Role> roles = jwtUtil.extractRoles(token);
        assertThat(roles).isEmpty();
    }

    @Test
    void isTokenExpired_freshToken_returnsFalse() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }

    @Test
    void isTokenValid_validToken_returnsTrue() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = jwtUtil.generateAccessToken("user@example.com", Set.of(Role.USER));
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_randomString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("this.is.not.a.jwt")).isFalse();
    }

    @Test
    void isTokenValid_emptyString_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void isTokenValid_tokenSignedWithDifferentSecret_returnsFalse() {
        JwtUtil otherJwt = new JwtUtil(
                "completely-different-secret-key-that-is-long-enough-here",
                ACCESS_EXP, REFRESH_EXP);
        String foreignToken = otherJwt.generateAccessToken("user@example.com", Set.of(Role.USER));
        assertThat(jwtUtil.isTokenValid(foreignToken)).isFalse();
    }

    @Test
    void accessToken_fullRoundTrip_emailAndRolesPreserved() {
        String email = "roundtrip@example.com";
        Set<Role> roles = Set.of(Role.PREMIUM, Role.USER);
        String token = jwtUtil.generateAccessToken(email, roles);

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
        assertThat(jwtUtil.extractRoles(token)).containsExactlyInAnyOrderElementsOf(roles);
    }

    @Test
    void refreshToken_fullRoundTrip_emailPreserved() {
        String email = "refresh@example.com";
        String token = jwtUtil.generateRefreshToken(email);

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo(email);
    }
}