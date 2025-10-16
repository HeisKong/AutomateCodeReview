package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.LoginRequest;
import com.automate.CodeReview.Models.RegisterRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.dto.ChangePasswordRequest;
import com.automate.CodeReview.dto.UpdateUserRequest;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final PasswordEncoder encoder;
    private final UsersRepository usersRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            AuthenticationManager authManager,
            PasswordEncoder encoder,
            UsersRepository usersRepository,
            JwtService jwtService,
            EmailService emailService,
            RefreshTokenService refreshTokenService
    ) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.usersRepository = usersRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }


    public LoginResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );
        UsersEntity u = usersRepository.findByEmail(req.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // เมธอดเดิม: คืนเฉพาะ access token (คงพฤติกรรมเดิม)
        String token = jwtService.generateAccessToken(u.getEmail());
        return new LoginResponse(token, toModel(u));
    }

    /** (เดิม) เช็คฟิลด์ซ้ำ */
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

    public void register(RegisterRequest req) {
        UsersEntity u = new UsersEntity();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPassword(encoder.encode(req.password())); // BCrypt
        u.setPhoneNumber(req.phoneNumber());
        u.setRole(normalizeRole("USER"));
        usersRepository.save(u);
    }

    @Transactional
    public UserModel updateUser(UpdateUserRequest req) {
        if (req.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }

        UsersEntity u = usersRepository.findById(req.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // check duplicates (เว้นตัวเอง)
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

        UserModel model = toModel(saved);
        model.setCreatedAt(saved.getCreatedAt());
        model.setPassword(null);
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
    public void changePassword(String principal, ChangePasswordRequest req) {
        Optional<UsersEntity> maybeUser = usersRepository.findByUsername(principal);
        if (maybeUser.isEmpty()) {
            maybeUser = usersRepository.findByEmail(principal);
        }
        if (maybeUser.isEmpty()) {
            try {
                UUID id = UUID.fromString(principal);
                maybeUser = usersRepository.findById(id);
            } catch (IllegalArgumentException ignored) {}
        }

        UsersEntity user = maybeUser
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password too weak (min 6 chars)");
        }
        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password and confirm do not match");
        }

        if (!user.isForcePasswordChange()) {
            if (req.getOldPassword() == null || !encoder.matches(req.getOldPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
            }
        }

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


    /** โครงผลลัพธ์สำหรับงาน login/refresh ที่ต้องคืน access+refresh */
    public record TokensResult(String accessToken, String refreshToken, UserModel user) {}

    /**
     * Login + ออกคู่ token (access+refresh) และบันทึก refresh ใน DB
     * ใช้ใน Controller ที่ต้องการ set-cookie("rt", refreshToken)
     */
    @Transactional
    public TokensResult loginIssueTokens(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );
        UsersEntity u = usersRepository.findByEmail(req.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        UUID jti = UUID.randomUUID();
        String access = jwtService.generateAccessToken(u.getEmail());
        String refresh = jwtService.generateRefreshToken(u.getEmail(), jti);

        // บันทึก refresh token ใน DB (expiry อ้างอิงจาก exp ใน JWT)
        refreshTokenService.create(u, refresh);

        return new TokensResult(access, refresh, toModel(u));
    }

    /**
     * รับ refresh token (จาก cookie) -> ตรวจสอบ/หมุน -> คืนคู่ token ใหม่
     */
    @Transactional
    public TokensResult refreshByToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token");
        }
        // ต้องเป็น refresh token จริง
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        // ต้องอยู่ใน DB และไม่หมดอายุ
        if (!refreshTokenService.existsValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        // หา user จาก subject ใน JWT
        String email = jwtService.validateTokenAndGetUsername(refreshToken);
        UsersEntity user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // ออกคู่ใหม่ + rotate
        UUID newJti = UUID.randomUUID();
        String newAccess = jwtService.generateAccessToken(email);
        String newRefresh = jwtService.generateRefreshToken(email, newJti);

        refreshTokenService.rotate(user, refreshToken, newRefresh);

        return new TokensResult(newAccess, newRefresh, toModel(user));
    }

    /**
     * Logout เฉพาะอุปกรณ์ปัจจุบัน: เพิกถอน refresh token ปัจจุบัน
     */
    @Transactional
    public void logoutCurrent(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return; // idempotent
        refreshTokenService.revoke(refreshToken);
    }

    /**
     * Logout ทุกอุปกรณ์ของผู้ใช้: เพิกถอน refresh token ทั้งหมด
     * ใช้ refresh token (จะหมดอายุก็อ่าน subject ได้)
     */
    @Transactional
    public void logoutAllDevices(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return; // idempotent
        String subject = jwtService.getSubjectEvenIfExpired(refreshToken);
        UsersEntity user = usersRepository.findByEmail(subject).orElse(null);
        if (user != null) {
            refreshTokenService.revokeAll(user);
        }
    }
}
