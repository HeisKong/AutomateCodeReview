package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.*;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.Service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
    record ApiMessage(String message) {}
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest req) {
        // 1) ให้ service เช็คว่าซ้ำช่องไหนบ้าง
        List<String> dups = authService.checkDuplicates(req);  // <- เขียนเพิ่มใน service ด้านล่าง

        // 2) ถ้ามีซ้ำ ส่ง 409 + fields กลับไป
        if (!dups.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Duplicate fields");
            body.put("fields", dups); // e.g. ["username","email","phoneNumber"]
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        // 3) ไม่ซ้ำ -> สร้างผู้ใช้ตามปกติ
        authService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiMessage("Registered"));
    }

}

