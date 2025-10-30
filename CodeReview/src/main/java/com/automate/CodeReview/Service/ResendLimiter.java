package com.automate.CodeReview.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ResendLimiter {
    private final Map<String, Long> lastSentMillis = new ConcurrentHashMap<>();
    private final long windowMs;

    public ResendLimiter(@Value("${auth.verify.resend.window-ms:120000}") long windowMs) {
        this.windowMs = windowMs; // ค่าเริ่มต้น 2 นาที
    }
    public boolean canSend(String key) {
        long now = System.currentTimeMillis();
        long last = lastSentMillis.getOrDefault(key, 0L);
        return now - last >= windowMs;
    }
    public void markSent(String key) {
        lastSentMillis.put(key, System.currentTimeMillis());
    }
    public long retryAfterSeconds(String key) {
        long now = System.currentTimeMillis();
        long last = lastSentMillis.getOrDefault(key, 0L);
        long remain = Math.max(0, windowMs - (now - last));
        return (long) Math.ceil(remain / 1000.0);
    }
}
