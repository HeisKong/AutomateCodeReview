package com.automate.CodeReview.Controller;

import com.automate.CodeReview.dto.request.LoginRequest;
import com.automate.CodeReview.dto.request.RegisterRequest;
import com.automate.CodeReview.dto.response.LoginResponse;
import com.automate.CodeReview.Service.AuthService;
import com.automate.CodeReview.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieUtil cookieUtil;
    // ใช้ค่านี้ตั้งอายุคุกกี้ rt ให้สอดคล้องกับ JWT refresh (ms)

    public AuthController(AuthService authService,
                          @Value("${jwt.refresh-ms}") long refreshMs) {
        this.authService = authService;
        this.cookieUtil = new CookieUtil(refreshMs, "None");
    }

    /* ===================== LOGIN ===================== */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest req,
                                               HttpServletResponse res) {

        // ออกคู่ token + บันทึก refresh ลง DB
        var r = authService.loginIssueTokens(req);

        // เซ็ต refresh token เป็น HttpOnly cookie
        cookieUtil.setRtCookie(res, r.refreshToken());

        return ResponseEntity.ok(new LoginResponse(r.accessToken()));
    }

    /* ===================== REFRESH ===================== */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse res) {
        String rt = cookieUtil.getRtCookie(req);
        if (rt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing refresh token");

        var r = authService.refreshByToken(rt);
        cookieUtil.setRtCookie(res, r.refreshToken());
        return ResponseEntity.ok(Map.of("accessToken", r.accessToken()));
    }

    /* ===================== LOGOUT (เครื่องนี้) ===================== */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest req, HttpServletResponse res) {
        String rt = cookieUtil.getRtCookie(req);
        authService.logoutCurrent(rt);
        cookieUtil.clearRtCookie(res);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    /* ===================== LOGOUT ทุกอุปกรณ์ ===================== */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(HttpServletRequest req, HttpServletResponse res) {
        String rt = cookieUtil.getRtCookie(req);
        authService.logoutAllDevices(rt);
        cookieUtil.clearRtCookie(res);
        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }

    record ApiMessage(String message) {}
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest req) {
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiMessage("Registered successfully"));
    }


}
