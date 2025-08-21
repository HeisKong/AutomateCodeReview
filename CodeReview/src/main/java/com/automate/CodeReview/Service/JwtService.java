package com.automate.CodeReview.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    private final Key key;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs
    ) {
        // ถ้า secret เป็น Base64 ให้ใช้ Decoders.BASE64.decode(secret) แทน getBytes
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email) {
        return generateToken(Map.of(), email);
    }

    public String generateToken(Map<String, Object> extraClaims, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(email)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject); // subject คือ email
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = parser().parseClaimsJws(token).getBody();
        return resolver.apply(claims);
    }

    public boolean isValid(String token, String email) {
        String tokenEmail = extractEmail(token);   // ดึง email จาก token
        return tokenEmail.equalsIgnoreCase(email) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        Date exp = extractClaim(token, Claims::getExpiration);
        return exp.before(new Date());
    }

    private JwtParser parser() {
        return Jwts.parserBuilder().setSigningKey(key).build();
    }
}
