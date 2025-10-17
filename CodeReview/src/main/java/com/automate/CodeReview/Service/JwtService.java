package com.automate.CodeReview.Service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
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
            @Value("${jwt.access-ms:90000000}") long accessMs,
            @Value("${jwt.refresh-ms:2592000000}") long refreshMs
    ) {
        this.key = buildKey(secret);
        this.parser = Jwts.parserBuilder().setSigningKey(this.key).build();
        this.accessMs = accessMs;
        this.refreshMs = refreshMs;
    }

    private Key buildKey(String secret) {
        // รองรับรูปแบบ base64:<KEY>
        if (secret != null && secret.startsWith("base64:")) {
            byte[] bytes = Decoders.BASE64.decode(secret.substring(7));
            // ต้องได้ >= 32 bytes (256-bit) ไม่งั้น JJWT จะโยน WeakKeyException
            return Keys.hmacShaKeyFor(bytes);
        }
        // ไม่ใช้ base64: ใช้สตริงตรง ๆ ต้องยาว >= 32 ไบต์
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /* ============ Access Token ============ */
    public String generateAccessToken(String subject) {
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .setExpiration(new Date(Instant.now().toEpochMilli() + accessMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /* ============ Refresh Token ============ */
    public String generateRefreshToken(String subject, UUID jti) {
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
        return parser.parseClaimsJws(token).getBody();
    }

    public String validateTokenAndGetUsername(String token) {
        return parseAllClaims(token).getSubject();
    }

    public boolean isRefreshToken(String token) {
        Object t = parseAllClaims(token).get("token_type");
        return "refresh".equals(String.valueOf(t));
    }

    public String getJti(String token) {
        Object j = parseAllClaims(token).get("jti");
        return j == null ? null : j.toString();
    }

    /** ใช้ตอน logout-all: อ่าน subject ได้แม้หมดอายุ */
    public String getSubjectEvenIfExpired(String token) {
        try { return validateTokenAndGetUsername(token); }
        catch (ExpiredJwtException e) { return e.getClaims().getSubject(); }
    }

    /** ออก token เป็นคู่ */
    public TokenPair issueTokens(String subject) {
        UUID jti = UUID.randomUUID();
        return new TokenPair(
                generateAccessToken(subject),
                generateRefreshToken(subject, jti),
                jti
        );
    }

    public record TokenPair(String accessToken, String refreshToken, UUID jti) {}
}
