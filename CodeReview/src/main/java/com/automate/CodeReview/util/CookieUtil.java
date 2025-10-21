package com.automate.CodeReview.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.util.WebUtils;
import java.time.Duration;

public class CookieUtil {

    private final long refreshMs;
    private final String sameSite;

    public CookieUtil(long refreshMs, String sameSite) {
        this.refreshMs = refreshMs;
        this.sameSite = sameSite;
    }

    // ===== GET =====
    public String getRtCookie(HttpServletRequest req) {
        Cookie ck = WebUtils.getCookie(req, "rt");
        if (ck == null) return null;

        String v = ck.getValue();
        if (v == null) return null;

        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    // ===== SET =====
    public void setRtCookie(HttpServletResponse res, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("rt", refreshToken)
                .httpOnly(true)
                .secure(true) // ใช้ HTTPS ถ้า SameSite=None
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshMs))
                .sameSite(sameSite)
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    // ===== CLEAR =====
    public void clearRtCookie(HttpServletResponse res) {
        ResponseCookie del = ResponseCookie.from("rt", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/auth")
                .maxAge(0)
                .sameSite(sameSite)
                .build();

        res.addHeader(HttpHeaders.SET_COOKIE, del.toString());
    }
}
