package com.automate.CodeReview.Utilities;
import java.time.*; import java.time.format.DateTimeFormatter;
import java.util.*; import java.util.regex.*;

public class OwaspUtil {
    private static final DateTimeFormatter SONAR_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    public static Instant parseSonarInstant(String s) {
        return (s==null)? null : ZonedDateTime.parse(s, SONAR_TS).toInstant();
    }

    private static final Map<String,String> OWASP2021 = Map.ofEntries(
            Map.entry("A01","Broken Access Control"),
            Map.entry("A02","Cryptographic Failures"),
            Map.entry("A03","Injection"),
            Map.entry("A04","Insecure Design"),
            Map.entry("A05","Security Misconfiguration"),
            Map.entry("A06","Vulnerable and Outdated Components"),
            Map.entry("A07","Identification and Authentication Failures"),
            Map.entry("A08","Software and Data Integrity Failures"),
            Map.entry("A09","Security Logging and Monitoring Failures"),
            Map.entry("A10","Server-Side Request Forgery (SSRF)")
    );
    public static String owaspName(String code){ return OWASP2021.getOrDefault(code, code); }

    private static final Pattern OWASP_TAG = Pattern.compile("owasp-a(\\d{2})", Pattern.CASE_INSENSITIVE);
    public static String extractOwaspCodeFromTags(List<String> tags){
        if (tags==null) return null;
        for (String t : tags) {
            var m = OWASP_TAG.matcher(t);
            if (m.matches()) return "A" + m.group(1);
        }
        return null;
    }
}

