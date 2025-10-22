package com.automate.CodeReview.Config;

import com.automate.CodeReview.Filter.JwtFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }


    /* ===== CORS: อนุญาต origin ของ frontend และส่งคุกกี้ข้ามโดเมนได้ (แก้ไข) ===== */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://localhost:4200"
        ));

        // Allowed Methods ต้องรวม OPTIONS เพื่อให้ Preflight Request ผ่าน
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
                .csrf(csrf -> csrf.disable())
                // 💡 การใช้ .cors(Customizer.withDefaults()) ในที่นี้ ถูกต้องแล้ว
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorizedEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // 💡 อนุญาต OPTIONS method สำหรับทุกเส้นทาง (จำเป็นสำหรับ CORS Preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/sonar/webhook").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        // 💡 เส้นทางรีเซ็ตรหัสผ่านต้องเปิด permitAll
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/auth/password-reset/**",
                                "/api/auth/login",
                                "/api/auth/register"
                        ).permitAll()
                        .requestMatchers("/api/scans/**").hasAnyRole("USER","ADMIN")
                        .requestMatchers("/api/sonar/logfile").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /* ===== 401/403 JSON responses (ไม่มีการเปลี่ยนแปลง) ===== */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 401,
                    "error", "Unauthorized",
                    "message", "Authentication required or token invalid"
            ));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(), Map.of(
                    "timestamp", Instant.now().toString(),
                    "status", 403,
                    "error", "Forbidden",
                    "message", "ADMIN role required"
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