package com.automate.CodeReview.Filter;

import com.automate.CodeReview.Service.JwtService;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
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
import java.util.stream.Collectors;

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

        return path.startsWith("/api/auth")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.equals("/api/sonar/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();


        try {
            String token = extractTokenFromRequest(request);

            if (token != null) {
                log.debug("🔹 Token extracted: {}...", token.substring(0, Math.min(20, token.length())));
                authenticateToken(request, token);
            } else {
                log.debug("🔹 No Bearer token found in Authorization header");
            }


        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);

        var postCtx = SecurityContextHolder.getContext();
        if (postCtx.getAuthentication() != null) {
            log.info("🧩 [JwtFilter] After chain: still authenticated as {}", postCtx.getAuthentication().getName());
        } else {
            log.warn("🧨 [JwtFilter] After chain: authentication was cleared");
        }

        log.info("🔚 [JwtFilter] End for [{} {}]", method, uri);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            authHeader = request.getHeader("authorization"); // รองรับ lowercase
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private void authenticateToken(HttpServletRequest request, String token) {
        try {
            Claims claims = jwtService.validateAndParseClaims(token);
            String email = claims.get("email", String.class);
            String tokenType = claims.get("token_type", String.class);
            if (!"access".equalsIgnoreCase(tokenType)) return;

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Object rolesClaim = claims.get("roles");
                List<SimpleGrantedAuthority> authorities;

                if (rolesClaim instanceof List<?> roleList) {
                    authorities = roleList.stream()
                            .map(r -> {
                                String role = (r instanceof String s) ? s : r.toString();
                                role = role.replaceAll("[\\[\\]\\s]", ""); // ลบ []
                                role = role.toUpperCase();
                                if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                                return new SimpleGrantedAuthority(role);
                            })
                            .collect(Collectors.toList());
                } else if (rolesClaim != null) {
                    String role = rolesClaim.toString().replaceAll("[\\[\\]\\s]", "").toUpperCase();
                    if (!role.startsWith("ROLE_")) role = "ROLE_" + role;
                    authorities = List.of(new SimpleGrantedAuthority(role));
                } else {
                    authorities = List.of();
                }

                Optional<UsersEntity> userOpt = usersRepository.findByEmail(email);
                if (userOpt.isEmpty()) return;

                var authToken = new UsernamePasswordAuthenticationToken(
                        email, null, authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();

        } catch (JwtException ex) {
            SecurityContextHolder.clearContext();

        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }
    }
}