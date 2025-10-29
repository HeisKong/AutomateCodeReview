package com.automate.CodeReview.Service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;

@Service
public class VerificationTokenService {

    // key สำหรับ sign token (ควรย้ายไป config หรือ .env)
    private final Key secretKey = Keys.hmacShaKeyFor("verysecretandlongemailverificationkey123!".getBytes());

    // อายุของ token (24 ชั่วโมง)
    private static final long EXPIRE_MS = 24 * 60 * 60 * 1000;

    // ออก token
    public String generateToken(String email) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + EXPIRE_MS);

        return Jwts.builder()
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(exp)
                .claim("type", "email_verification")
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    // ตรวจและคืนค่า email
    public String validateAndGetEmail(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            if (!"email_verification".equals(claims.get("type", String.class))) {
                throw new JwtException("Invalid token type");
            }

            return claims.getSubject(); // email
        } catch (ExpiredJwtException e) {
            throw new JwtException("Verification link expired");
        } catch (Exception e) {
            throw new JwtException("Invalid verification token");
        }
    }
}
