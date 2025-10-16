package com.automate.CodeReview.Service;

import com.automate.CodeReview.dto.ChangePasswordRequest;
import com.automate.CodeReview.Models.LoginRequest;
import com.automate.CodeReview.Models.RegisterRequest;
import com.automate.CodeReview.dto.UpdateUserRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.SecureRandom.PasswordUtils;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;

import com.automate.CodeReview.service.EmailService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final PasswordEncoder encoder;
    private final UsersRepository usersRepository;
    private final JwtService jwtService;
    private final EmailService emailService;

    public AuthService(AuthenticationManager authManager,
                       PasswordEncoder encoder,
                       UsersRepository usersRepository,
                       JwtService jwtService, EmailService emailService) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.usersRepository = usersRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
    }

    public LoginResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );
        UsersEntity u = usersRepository.findByEmail(req.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtService.generateToken(u.getEmail());
        return new LoginResponse(token, toModel(u));
    }

    public void register(RegisterRequest req) {
        // ❌ ไม่เช็คซ้ำ/ไม่โยนที่นี่แล้ว (ให้ Controller ทำก่อนเรียกเมธอดนี้)
        UsersEntity u = new UsersEntity();
        u.setUsername(req.username().trim());
        u.setEmail(req.email().trim());
        u.setPassword(encoder.encode(req.password()));
        u.setPhoneNumber(req.phoneNumber().trim());
        u.setRole(normalizeRole("user"));
        usersRepository.save(u);
    }

    /** ให้ Controller เรียกใช้เพื่อเช็คว่าฟิลด์ไหนซ้ำบ้าง */
    public List<String> checkDuplicates(RegisterRequest req) {
        List<String> fields = new ArrayList<>();
        if (usersRepository.existsByUsername(req.username()))       fields.add("username");
        if (usersRepository.existsByEmail(req.email()))             fields.add("email");
        if (usersRepository.existsByPhoneNumber(req.phoneNumber())) fields.add("phoneNumber");
        return fields;
    }

    private UserModel toModel(UsersEntity e) {
        UserModel m = new UserModel();
        m.setId(e.getUserId());
        m.setUsername(e.getUsername());
        m.setEmail(e.getEmail());
        m.setPhoneNumber(e.getPhoneNumber());
        m.setRole(e.getRole());
        m.setCreatedAt(e.getCreatedAt());
        return m;
    }

    @Transactional
    public UserModel updateUser(UpdateUserRequest req) {
        if (req.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }

        UsersEntity u = usersRepository.findById(req.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // check duplicates (ยกเว้นตัวเอง)
        if (req.getUsername() != null && !req.getUsername().equals(u.getUsername())
                && usersRepository.existsByUsername(req.getUsername())) {
            throw new DuplicateKeyException("Username already exists");
        }
        if (req.getEmail() != null && !req.getEmail().equals(u.getEmail())
                && usersRepository.existsByEmail(req.getEmail())) {
            throw new DuplicateKeyException("Email already exists");
        }
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().equals(u.getPhoneNumber())
                && usersRepository.existsByPhoneNumber(req.getPhoneNumber())) {
            throw new DuplicateKeyException("Phone number already exists");
        }

        // update fields (ไม่แก้ password)
        if (req.getUsername() != null) u.setUsername(req.getUsername());
        if (req.getEmail() != null) u.setEmail(req.getEmail());
        if (req.getPhoneNumber() != null) u.setPhoneNumber(req.getPhoneNumber());
        if (req.getRole() != null) u.setRole(normalizeRole(req.getRole()));

        UsersEntity saved = usersRepository.save(u);

        // แปลงเป็น model พร้อม createdAt แต่ไม่คืน password
        UserModel model = toModel(saved);
        model.setCreatedAt(saved.getCreatedAt()); // เพิ่ม createdAt ใน response
        model.setPassword(null); // ป้องกันส่ง password
        return model;
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }
        if (!usersRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        usersRepository.deleteById(id);
    }
    @Transactional
    public ResponseEntity<Map<String, Object>> adminResetPassword(String email) {
        UsersEntity user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // สร้าง temp password
        String tempPassword = PasswordUtils.generateTempPassword(12);

        // แฮชแล้วเซฟ
        user.setPassword(encoder.encode(tempPassword));
        user.setForcePasswordChange(true);
        usersRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());

        // ส่งไปยังอีเมลผู้ใช้
        try {
            emailService.sendResetPassword(user.getEmail(), tempPassword);
            response.put("status", "SUCCESS");
            response.put("tempPassword", tempPassword); // สำหรับ debug / testing
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "FAILED");
            response.put("message", "Failed to send reset password email");
            // ถ้าอยากให้รหัสผ่านเปลี่ยนจริง แม้ส่งเมลล้มเหลว -> ไม่ throw exception
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @Transactional
    public void changePassword(String principal, ChangePasswordRequest req) {
        // ลองหา user ด้วยหลายทาง (username, email, uuid)
        Optional<UsersEntity> maybeUser = usersRepository.findByUsername(principal);
        if (maybeUser.isEmpty()) {
            maybeUser = usersRepository.findByEmail(principal);
        }
        if (maybeUser.isEmpty()) {
            // ลอง parse เป็น UUID ถ้า token เก็บ id เป็น sub
            try {
                UUID id = UUID.fromString(principal);
                maybeUser = usersRepository.findById(id);
            } catch (IllegalArgumentException ignored) {}
        }

        UsersEntity user = maybeUser
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // validate new password
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) { // ปรับตามนโยบาย
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password too weak (min 6 chars)");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirm do not match");
        }

        // ถ้า user ถูกบังคับให้เปลี่ยนพาสเวิร์ด (admin reset) -> oldPassword เป็น optional
        if (!user.isForcePasswordChange()) {
            if (req.getOldPassword() == null || !encoder.matches(req.getOldPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
            }
        } // else: ถ้ forcePasswordChange==true ให้เปลี่ยนได้โดยไม่ต้องมี oldPassword

        user.setPassword(encoder.encode(req.getNewPassword()));
        user.setForcePasswordChange(false);
        usersRepository.save(user);
    }

    private String normalizeRole(String role) {
        if (role == null) return "USER";
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) role = role.substring(5);
        return "ADMIN".equals(role) ? "ADMIN" : "USER";
    }


}

