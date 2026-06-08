package org.example;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(jwtUtil);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_noAuthHeader_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_authHeaderWithoutBearer_proceedsWithoutAuthentication() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validToken_setsAuthentication() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.isTokenExpired(token)).thenReturn(false);
        when(jwtUtil.extractEmail(token)).thenReturn("alice@example.com");
        when(jwtUtil.extractRoles(token)).thenReturn(Set.of(Role.USER));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("alice@example.com");
    }

    @Test
    void doFilter_validToken_setsCorrectAuthorities() throws Exception {
        String token = "valid.jwt.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.isTokenExpired(token)).thenReturn(false);
        when(jwtUtil.extractEmail(token)).thenReturn("admin@example.com");
        when(jwtUtil.extractRoles(token)).thenReturn(Set.of(Role.ADMIN));

        filter.doFilterInternal(request, response, filterChain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities).anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    void doFilter_multipleRoles_setsAllAuthorities() throws Exception {
        String token = "multi.role.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.isTokenExpired(token)).thenReturn(false);
        when(jwtUtil.extractEmail(token)).thenReturn("power@example.com");
        when(jwtUtil.extractRoles(token)).thenReturn(Set.of(Role.USER, Role.PREMIUM));

        filter.doFilterInternal(request, response, filterChain);

        var authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        assertThat(authorities)
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"))
                .anyMatch(a -> a.getAuthority().equals("ROLE_PREMIUM"));
    }

    @Test
    void doFilter_expiredToken_doesNotSetAuthentication() throws Exception {
        String token = "expired.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.isTokenExpired(token)).thenReturn(true);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_invalidToken_doesNotSetAuthentication() throws Exception {
        String token = "invalid.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_jwtUtilThrowsException_proceedsWithoutAuthentication() throws Exception {
        String token = "throws.exception";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenThrow(new RuntimeException("Unexpected error"));

        // Should NOT throw — exception is caught internally
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_validTokenNullEmail_doesNotSetAuthentication() throws Exception {
        String token = "null.email.token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.isTokenValid(token)).thenReturn(true);
        when(jwtUtil.isTokenExpired(token)).thenReturn(false);
        when(jwtUtil.extractEmail(token)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}