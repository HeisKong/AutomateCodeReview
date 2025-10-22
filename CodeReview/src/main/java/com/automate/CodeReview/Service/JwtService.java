package com.automate.CodeReview.Service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class JwtService {

    private final Key key;
    private final JwtParser parser;

    private final long accessMs;
    private final long refreshMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ms}") long accessMs,
            @Value("${jwt.refresh-ms}") long refreshMs
    ) {
        this.key = buildKey(secret);
        this.parser = Jwts.parserBuilder().setSigningKey(this.key).build();
        this.accessMs = accessMs;
        this.refreshMs = refreshMs;
    }

    private Key buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or empty");
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 bytes)");
        }
        // กรณีไม่ใช้ base64 ให้แน่ใจว่ายาว >= 32 ไบต์
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(UUID userId,String email, String username,String roles) {
        if(email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be null or empty");
        }
        if(username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be null or empty");
        }
        if(roles == null || roles.isBlank()) {
            throw new IllegalArgumentException("Roles must not be null or empty");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("user_id", userId);
        claims.put("email", email);
        claims.put("username", username);
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(Instant.now().toEpochMilli() + accessMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /* ============ Refresh Token ============ */
    public String generateRefreshToken(String subject, UUID jti) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject must not be null");
        }
        if (jti == null) {
            throw new IllegalArgumentException("JTI must not be null");
        }
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(Map.of("token_type", "refresh", "jti", jti.toString()))
                .setIssuedAt(new Date())
                .setExpiration(new Date(Instant.now().toEpochMilli() + refreshMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /* ============ Validation / Parsing ============ */
    public Claims parseAllClaims(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }
        try {
            return parser.parseClaimsJws(token).getBody();
        } catch (ExpiredJwtException e) {
            throw new JwtException("Token has expired", e);
        } catch (UnsupportedJwtException e) {
            throw new JwtException("Unsupported JWT token", e);
        } catch (MalformedJwtException e) {
            throw new JwtException("Invalid JWT token", e);
        } catch (SignatureException e) {
            throw new JwtException("Invalid JWT signature", e);
        } catch (IllegalArgumentException e) {
            throw new JwtException("JWT claims string is empty", e);
        }
    }

    public String validateTokenAndGetUsername(String token) {
        return parseAllClaims(token).getSubject();
    }

    public boolean isRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return  false;
        }
        try {
            Object t = parseAllClaims(token).get("token_type");
            return "refresh".equals(String.valueOf(t));
        }catch (Exception e) {
            return false;
        }
    }

    public String getSubjectEvenIfExpired(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be null or empty");
        }

        try {
            return validateTokenAndGetUsername(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (Exception e) {
            throw new JwtException("Cannot extract subject from token", e);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, UUID jti) {}
}
