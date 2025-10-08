package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.LoginRequest;
import com.automate.CodeReview.Models.RegisterRequest;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.Service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    // ใช้ค่านี้ตั้งอายุคุกกี้ rt ให้สอดคล้องกับ JWT refresh (ms)
    private final long refreshMs;

    public AuthController(AuthService authService,
                          @Value("${jwt.refresh-ms:604800000}") long refreshMs) {
        this.authService = authService;
        this.refreshMs = refreshMs;
    }

    /* ===================== LOGIN ===================== */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest req,
                                               HttpServletResponse res) {

        // ออกคู่ token + บันทึก refresh ลง DB
        var r = authService.loginIssueTokens(req);

        // เซ็ต refresh token เป็น HttpOnly cookie
        setRtCookie(res, r.refreshToken());

        // คงพฤติกรรมเดิม: คืน LoginResponse (accessToken + user)
        return ResponseEntity.ok(new LoginResponse(r.accessToken(), r.user()));
    }

    /* ===================== REFRESH ===================== */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse res) {
        String rt = getRtCookie(req);
        var r = authService.refreshByToken(rt);

        // ออก refresh ใหม่แล้ว -> ตั้งคุกกี้ใหม่ทับ
        setRtCookie(res, r.refreshToken());

        // คืน access token ใหม่
        return ResponseEntity.ok(Map.of("accessToken", r.accessToken()));
    }

    /* ===================== LOGOUT (เครื่องนี้) ===================== */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
        String rt = getRtCookie(req);
        authService.logoutCurrent(rt);
        clearRtCookie(res);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    /* ===================== LOGOUT ทุกอุปกรณ์ ===================== */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(HttpServletRequest req, HttpServletResponse res) {
        String rt = getRtCookie(req);
        authService.logoutAllDevices(rt);
        clearRtCookie(res);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    /* ===================== REGISTER (เดิม) ===================== */
    record ApiMessage(String message) {}
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest req) {
        List<String> dups = authService.checkDuplicates(req);
        if (!dups.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Duplicate fields");
            body.put("fields", dups);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiMessage("Registered"));
    }

    /* ===================== Helpers ===================== */

    private String getRtCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if ("rt".equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private void setRtCookie(HttpServletResponse res, String refreshToken) {
        Cookie c = new Cookie("rt", refreshToken);
        c.setHttpOnly(true);
        c.setSecure(true);         // ควรเปิดใช้จริงเมื่อเป็น HTTPS
        c.setPath("/api/auth");
        // ตั้งอายุคุกกี้ตาม refresh TTL (วินาที)
        int maxAgeSec = (int) Math.max(1, refreshMs / 1000);
        c.setMaxAge(maxAgeSec);
        res.addCookie(c);

        // ถ้าต้องใช้ข้ามโดเมน: เพิ่ม SameSite=None แบบกำหนด header เอง (เลือกใช้เมื่อจำเป็น)
        // res.addHeader("Set-Cookie",
        //   "rt=" + refreshToken + "; Path=/api/auth; HttpOnly; Secure; Max-Age=" + maxAgeSec + "; SameSite=None");
    }

    private void clearRtCookie(HttpServletResponse res) {
        Cookie c = new Cookie("rt", "");
        c.setHttpOnly(true);
        c.setSecure(true);
        c.setPath("/api/auth");
        c.setMaxAge(0);
        res.addCookie(c);

        // แบบกำหนด header เองสำหรับ SameSite=None (ถ้าเคยตั้งไว้)
        // res.addHeader("Set-Cookie", "rt=; Path=/api/auth; HttpOnly; Secure; Max-Age=0; SameSite=None");
    }
}
