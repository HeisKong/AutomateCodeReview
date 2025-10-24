package com.automate.CodeReview.Config;

import com.automate.CodeReview.Filter.JwtFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:4200", "http://localhost"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           AuthenticationEntryPoint unauthorizedEntryPoint,
                                           AccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                // ✅ ลบ .anonymous(AbstractHttpConfigurer::disable) ออก
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())

                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ✅ เพิ่ม SecurityContextHolderFilter เพื่อให้แน่ใจว่า SecurityContext persist
                .securityContext(context -> context
                        .requireExplicitSave(false)
                )

                // ✅ เพิ่ม JwtFilter ก่อน UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // ✅ Debug: ดูว่า filter chain ทำงานอย่างไร
                .addFilterBefore(
                        (request, response, chain) -> {
                            log.debug("🔵 [Before Authorization] URI: {}, Auth: {}",
                                    ((jakarta.servlet.http.HttpServletRequest)request).getRequestURI(),
                                    SecurityContextHolder.getContext().getAuthentication() != null ?
                                            SecurityContextHolder.getContext().getAuthentication().getName() : "null"
                            );
                            chain.doFilter(request, response);
                        },
                        org.springframework.security.web.access.intercept.AuthorizationFilter.class
                )

                .authorizeHttpRequests(auth -> auth
                        // ✅ OPTIONS requests (CORS preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // ✅ Swagger/OpenAPI docs
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

                        // ✅ Public auth endpoints
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/password-reset/**"
                        ).permitAll()

                        // ✅ Sonar webhook (public endpoint สำหรับรับ callback)
                        .requestMatchers("/api/sonar/webhook").permitAll()

                        // ✅ Scans endpoints
                        .requestMatchers("/api/scans/**").hasAnyRole("USER", "ADMIN")

                        // ✅ ทุก request อื่นๆ ต้อง authenticated
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    /* ===== Custom 401/403 JSON responses ===== */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> {
            log.warn("🚫 Unauthorized access attempt: {} {}", request.getMethod(), request.getRequestURI());
            log.warn("🚫 Reason: {}", ex.getMessage());

            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Authentication required or token invalid/expired",
                    "path", request.getRequestURI()
            ));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            log.warn("🚫 Access denied: {} {}", request.getMethod(), request.getRequestURI());
            log.warn("🚫 User: {}", request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous");
            log.warn("🚫 Reason: {}", ex.getMessage());

            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 403,
                    "error", "Forbidden",
                    "message", "Access denied. Insufficient privileges.",
                    "path", request.getRequestURI()
            ));
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter loggingFilter = new CommonsRequestLoggingFilter();
        loggingFilter.setIncludeClientInfo(true);
        loggingFilter.setIncludeHeaders(true);
        loggingFilter.setIncludePayload(true);
        loggingFilter.setMaxPayloadLength(64000);
        return loggingFilter;
    }
}