package com.automate.CodeReview.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
public class TokenHashUtils {

    public static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error hashing token with SHA-256", e);
        }
    }
}
