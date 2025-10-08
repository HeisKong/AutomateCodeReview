package com.automate.CodeReview.Filter;

import com.automate.CodeReview.Service.JwtService;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsersRepository usersRepository;

    public JwtFilter(JwtService jwtService, UsersRepository usersRepository) {
        this.jwtService = jwtService;
        this.usersRepository = usersRepository;
    }

    /** ข้ามเส้นทางที่ไม่ต้องตรวจ และ preflight */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        return path.startsWith("/api/auth")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/swagger-ui.html")
                || path.startsWith("/api/sonar/webhook");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // ไม่มี Bearer ก็ปล่อยผ่านไป -> ถ้าปลายทาง require auth ระบบจะยิง 401 เอง
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        String subject; // เราใช้ email เป็น subject
        try {
            subject = jwtService.validateTokenAndGetUsername(token);
        } catch (Exception ex) {
            // token ใช้ไม่ได้ -> ไม่ตั้ง auth ให้ และปล่อยให้ entry point ตอบ 401
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        // ยังไม่มี auth ใน context ค่อยตั้งให้
        if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // ค้นหาผู้ใช้ด้วย email (เพราะเราออก token ใส่ sub=email)
            Optional<UsersEntity> userOpt = usersRepository.findByEmail(subject);
            if (userOpt.isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            UsersEntity user = userOpt.get();
            var authToken = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), // principal
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }

        filterChain.doFilter(request, response);
    }
}
