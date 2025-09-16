package com.automate.CodeReview.utilities;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OwaspUtil {

    // ===== OWASP Top 10 name mapping =====
    private static final Map<String, String> NAME = Map.ofEntries(
            Map.entry("A01", "Broken Access Control"),
            Map.entry("A02", "Cryptographic Failures"),
            Map.entry("A03", "Injection"),
            Map.entry("A04", "Insecure Design"),
            Map.entry("A05", "Security Misconfiguration"),
            Map.entry("A06", "Vulnerable and Outdated Components"),
            Map.entry("A07", "Identification and Authentication Failures"),
            Map.entry("A08", "Software and Data Integrity Failures"),
            Map.entry("A09", "Security Logging and Monitoring Failures"),
            Map.entry("A10", "Server-Side Request Forgery (SSRF)")
    );

    public static String owaspName(String code) {
        return NAME.getOrDefault(code, code);
    }

    // ===== Extract OWASP code from tags =====
    // รองรับ owasp-a03 / owasp_a03 (ตัวพิมพ์เล็ก/ใหญ่ได้ทั้งหมด)
    private static final Pattern OWASP_TAG = Pattern.compile("owasp[-_]?a(\\d{2})", Pattern.CASE_INSENSITIVE);

    /** สำหรับกรณี tags เป็นข้อความเดียว (CSV หรือ string เดียวจาก DB) */
    public static String extractOwaspCodeFromTags(String tags) {
        if (tags == null || tags.isBlank()) return null;
        Matcher m = OWASP_TAG.matcher(tags);
        return m.find() ? ("A" + m.group(1)) : null;
        // ได้ผลลัพธ์เช่น "A03"
    }

    /** สำหรับกรณี tags เป็น List<String> (จาก Sonar Web API) */
    public static String extractOwaspCodeFromTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) return null;
        for (String t : tags) {
            String c = extractOwaspCodeFromTags(t);
            if (c != null) return c;
        }
        return null;
    }

    // ===== Parse Sonar date/time to Instant =====
    /** รับได้ทั้ง Instant / Number (epoch millis) / String (ISO +07:00, +0700, หรือ epoch) */
    public static Instant parseSonarInstant(Object v) {
        if (v == null) return null;
        if (v instanceof Instant i) return i;
        if (v instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        return parseSonarInstant(v.toString());
    }

    public static Instant parseSonarInstant(String s) {
        if (s == null || s.isBlank()) return null;

        // 1) ISO_OFFSET_DATE_TIME เช่น 2025-09-01T10:21:33+07:00 หรือ Z
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (Exception ignore) {}

        // 2) ISO offset แบบไม่มีโคลอนที่ท้าย เช่น +0700
        try {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
            return OffsetDateTime.parse(s, f).toInstant();
        } catch (Exception ignore) {}

        // 3) epoch millis ในรูป string
        try {
            long epoch = Long.parseLong(s.trim());
            return Instant.ofEpochMilli(epoch);
        } catch (Exception ignore) {}

        return null;
    }
}
