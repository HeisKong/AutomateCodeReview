package com.automate.CodeReview.Controller;

import com.automate.CodeReview.Models.UpdateUserRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Service.AuthService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserModel> updateUser(@RequestBody UpdateUserRequest req) {
        UserModel updated = authService.updateUser(req);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> deleteUser(@PathVariable("id") UUID id) {
        authService.deleteUser(id);
        return ResponseEntity.ok(Map.of(
                "message", "User deleted successfully",
                "userId", id.toString()
        ));
    }
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')") // เฉพาะ ADMIN
    public ResponseEntity<?> resetPassword(@RequestParam("email") String email) {

        // คืน temp password จาก service
        String tempPassword = authService.adminResetPassword(email);

        // DEV ONLY: คืน temp password ใน response — ห้ามใช้ใน production
        return ResponseEntity.ok(Map.of(
                "message", "Temporary password generated (dev only)",
                "tempPassword", tempPassword
        ));
    }
}
