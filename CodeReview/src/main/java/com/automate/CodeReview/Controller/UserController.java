package com.automate.CodeReview.Controller;

import com.automate.CodeReview.dto.ChangePasswordRequest;
import com.automate.CodeReview.dto.UpdateUserRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@CrossOrigin(origins = "http://localhost:4200")
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

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String,String>> changePassword(
            @RequestBody ChangePasswordRequest req,
            Authentication authentication) {

        String principal = (authentication != null) ? authentication.getName() : null;
        System.out.println("[DEBUG] Authentication object: " + authentication);
        System.out.println("[DEBUG] principal: '" + principal + "'");

        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");

        authService.changePassword(principal, req);
        return ResponseEntity.ok(Map.of("message","Password changed successfully"));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<UserModel>> getAllUsers() {
        return ResponseEntity.ok(authService.listAllUsers());
    }


}


