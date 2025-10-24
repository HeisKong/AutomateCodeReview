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

        String uri = request.getRequestURI();
        String method = request.getMethod();

        log.info("🟢 [JwtFilter] Start for [{} {}]", method, uri);

        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null) {
                authHeader = request.getHeader("authorization"); // รองรับ lowercase
            }

            log.debug("🔹 Authorization header: {}", authHeader);

            String token = extractTokenFromRequest(request);
            authenticateToken(request, token);

            // ✅ หลังจาก authenticate เสร็จ ลอง log context ปัจจุบัน
            var ctx = SecurityContextHolder.getContext();
            if (ctx.getAuthentication() != null) {
                log.info("🔒 SecurityContext now holds authentication: {}", ctx.getAuthentication().getName());
                log.debug("🔸 Authorities: {}", ctx.getAuthentication().getAuthorities());
            } else {
                log.warn("⚠️ SecurityContext is still empty after JwtFilter (no authentication)");
            }

        } catch (Exception ex) {
            log.error("❌ Unexpected error in JWT filter for [{} {}]: {}", method, uri, ex.getMessage(), ex);
            SecurityContextHolder.clearContext();
        }

        log.info("➡️ [JwtFilter] Passing request [{} {}] to next filter...", method, uri);
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
            if (!"access".equalsIgnoreCase(tokenType)) {
                return;
            }

            if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                Object rolesClaim = claims.get("roles");
                List<SimpleGrantedAuthority> authorities;

                if (rolesClaim instanceof List<?> roleList) {
                    // สำหรับ List: map และจัดการ prefix
                    authorities = roleList.stream()
                            .map(r -> {
                                String role = r.toString();
                                // เพิ่ม ROLE_ ให้ถ้ายังไม่มี
                                return new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role);
                            })
                            .collect(Collectors.toList());
                } else {
                    // สำหรับ String/Single Object: จัดการ prefix
                    String role = rolesClaim.toString();
                    authorities = List.of(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role));
                }

                Optional<UsersEntity> userOpt = usersRepository.findByEmail(email);
                if (userOpt.isEmpty()) {
                    return;
                }

                var authToken = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        authorities
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

            }

        } catch (ExpiredJwtException ex) {
            log.info("Token expired: {}", ex.getMessage());
            SecurityContextHolder.clearContext();

        } catch (JwtException ex) {
            log.warn("Invalid token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }
}
