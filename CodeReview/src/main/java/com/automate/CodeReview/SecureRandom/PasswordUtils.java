package com.automate.CodeReview.SecureRandom;

import java.security.SecureRandom;

public class PasswordUtils {
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_+=<>?";
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generateTempPassword(int length) {
        if (length < 8) length = 12;
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RANDOM.nextInt(CHARSET.length());
            sb.append(CHARSET.charAt(idx));
        }
        return sb.toString();
    }
}

