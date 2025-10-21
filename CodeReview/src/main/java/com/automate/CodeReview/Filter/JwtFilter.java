package com.automate.CodeReview.Filter;

import com.automate.CodeReview.Service.JwtService;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsersRepository usersRepository;

    public JwtFilter(JwtService jwtService, UsersRepository usersRepository) {
        this.jwtService = jwtService;
        this.usersRepository = usersRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        // Skip OPTIONS (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Skip public endpoints
        return path.startsWith("/api/auth")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/api/sonar/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (token != null) {
                authenticateToken(request, token);
            }
        } catch (Exception ex) {
            log.error("Unexpected error in JWT filter: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void authenticateToken(HttpServletRequest request, String token) {
        try {
            String email = jwtService.validateTokenAndGetUsername(token);

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Optional<UsersEntity> userOpt = usersRepository.findByEmail(email);

                if (userOpt.isEmpty()) {
                    log.warn("User not found for email: {}", email);
                    return;
                }

                UsersEntity user = userOpt.get();
                String roleWithPrefix = "ROLE_" + user.getRole();

                log.debug("Authenticating user: {} with role: {}", user.getEmail(), roleWithPrefix);

                var authToken = new UsernamePasswordAuthenticationToken(
                        user.getEmail(),
                        null,
                        List.of(new SimpleGrantedAuthority(roleWithPrefix))
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (Exception ex) {
            log.warn("Token authentication failed: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}