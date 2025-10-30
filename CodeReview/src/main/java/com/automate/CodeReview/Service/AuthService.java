package com.automate.CodeReview.Service;

import com.automate.CodeReview.dto.request.LoginRequest;
import com.automate.CodeReview.dto.request.RegisterRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.dto.response.UserSummary;
import com.automate.CodeReview.dto.request.ChangePasswordRequest;
import com.automate.CodeReview.dto.request.UpdateUserProfileRequest;
import com.automate.CodeReview.dto.request.UpdateUserRequest;
import com.automate.CodeReview.entity.UserStatus;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.exception.DuplicateFieldsException;
import com.automate.CodeReview.repository.UsersRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AuthService {

    private final PasswordEncoder encoder;
    private final UsersRepository usersRepository;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RefreshTokenService refreshTokenService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_MINUTES = 30;

    private final Map<String, Integer> loginAttempts = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lockoutTime = new java.util.concurrent.ConcurrentHashMap<>();

    public AuthService(
            PasswordEncoder encoder,
            UsersRepository usersRepository,
            JwtService jwtService,
            EmailService emailService,
            RefreshTokenService refreshTokenService
    ) {
        this.encoder = encoder;
        this.usersRepository = usersRepository;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.refreshTokenService = refreshTokenService;
    }

    // ========== VALIDATION METHODS ==========

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email format");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private void validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is required");
        }
        if (username.trim().length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be at least 3 characters");
        }
        if (username.trim().length() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must not exceed 50 characters");
        }
    }

    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone number is required");
        }
        if (!phoneNumber.matches("^[0-9+\\-() ]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid phone number format");
        }
    }

    private void checkFieldDuplicate(String field, String value, String currentValue, UUID userId) {
        if (value == null || value.isBlank()) return;
        if (currentValue != null && value.equals(currentValue)) return;

        boolean exists = switch (field.toLowerCase()) {
            case "username" -> usersRepository.existsByUsername(value);
            case "email" -> usersRepository.existsByEmail(value);
            case "phone_number" -> usersRepository.existsByPhoneNumber(value);
            default -> false;
        };

        if (exists) {
            throw new DuplicateKeyException(field + " already exists");
        }
    }

    // ========== USER MANAGEMENT ==========

    @Transactional
    public void register(RegisterRequest req) {
        // Validate inputs
        validateUsername(req.username());
        validateEmail(req.email());
        validatePassword(req.password());
        validatePhoneNumber(req.phoneNumber());

        // Check duplicates
        List<String> duplicates = checkDuplicates(req);
        if (!duplicates.isEmpty()) {
            throw new DuplicateFieldsException(duplicates);
        }

        // Create user
        UsersEntity user = new UsersEntity();
        user.setUsername(req.username().trim());
        user.setEmail(req.email().trim().toLowerCase());
        user.setPassword(encoder.encode(req.password()));
        user.setPhoneNumber(req.phoneNumber().trim());
        user.setRole(normalizeRole("USER"));
        user.setStatus(UserStatus.PENDING_VERIFICATION);

        try {
            usersRepository.save(user);
            log.info("User registered successfully: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to save user: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to register user");
        }

        // Send email
        try {
            emailService.sendRegistrationSuccess(user.getEmail(), user.getUsername());
            log.info("Registration successful for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to send registration email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public List<String> checkDuplicates(RegisterRequest req) {
        List<String> fields = new ArrayList<>();
        if (usersRepository.existsByUsername(req.username())) fields.add("username");
        if (usersRepository.existsByEmail(req.email())) fields.add("email");
        if (usersRepository.existsByPhoneNumber(req.phoneNumber())) fields.add("phoneNumber");
        return fields;
    }

    @Transactional(readOnly = true)
    public List<UserModel> listAllUsers() {
        return usersRepository.findAll()
                .stream()
                .map(this::toModel)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserSummary getUserSummaryById(UUID userId) {
        UsersEntity user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return new UserSummary(user.getUsername(), user.getEmail(),
                user.getStatus(), user.getPhoneNumber());
    }

    @Transactional
    public UserModel updateUser(UpdateUserRequest req) {
        if (req.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }

        UsersEntity user = usersRepository.findById(req.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Validate and check duplicates
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            validateUsername(req.getUsername());
            checkFieldDuplicate("username", req.getUsername(), user.getUsername(), user.getUserId());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            validateEmail(req.getEmail());
            checkFieldDuplicate("email", req.getEmail(), user.getEmail(), user.getUserId());
        }
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            validatePhoneNumber(req.getPhoneNumber());
            checkFieldDuplicate("phonenumber", req.getPhoneNumber(), user.getPhoneNumber(), user.getUserId());
        }

        // Update fields
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            user.setUsername(req.getUsername().trim());
        }
        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            user.setEmail(req.getEmail().trim().toLowerCase());
        }
        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(req.getPhoneNumber().trim());
        }
        if (req.getRole() != null && !req.getRole().isBlank()) {
            user.setRole(normalizeRole(req.getRole()));
        }

        UsersEntity saved = usersRepository.save(user);
        log.info("User updated: {} by admin", saved.getEmail());

        return toModel(saved);
    }

    @Transactional
    public UserModel updateUserProfile(UpdateUserProfileRequest req, String email) {
        UsersEntity user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean emailChanged = false;

        // Validate and check duplicates
        if (req.getUsername() != null && !req.getUsername().isBlank()) {
            validateUsername(req.getUsername());
            checkFieldDuplicate("username", req.getUsername(), user.getUsername(), user.getUserId());
            user.setUsername(req.getUsername().trim());
        }

        if (req.getEmail() != null && !req.getEmail().isBlank()) {
            validateEmail(req.getEmail());
            String newEmail = req.getEmail().trim().toLowerCase();
            if (!newEmail.equals(user.getEmail())) {
                checkFieldDuplicate("email", newEmail, user.getEmail(), user.getUserId());
                user.setEmail(newEmail);
                user.setStatus(UserStatus.PENDING_VERIFICATION);
                emailChanged = true;
            }
        }

        if (req.getPhoneNumber() != null && !req.getPhoneNumber().isBlank()) {
            validatePhoneNumber(req.getPhoneNumber());
            checkFieldDuplicate("phonenumber", req.getPhoneNumber(), user.getPhoneNumber(), user.getUserId());
            user.setPhoneNumber(req.getPhoneNumber().trim());
        }

        UsersEntity saved = usersRepository.save(user);
        log.info("User profile updated: {}", saved.getEmail());

        // Send verification email if email changed
        if (emailChanged) {
            try {
                // ใช้ sendRegistrationSuccess แทน sendEmailVerification ตามโค้ดเดิม
                emailService.sendRegistrationSuccess(saved.getEmail(), saved.getUsername());
            } catch (Exception e) {
                log.error("Failed to send verification email: {}", e.getMessage());
            }
        }

        UserModel model = toModel(saved);
        model.setCreatedAt(saved.getCreatedAt());
        return model;
    }

    @Transactional
    public void deleteUser(UUID id) {
        if (id == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing user id");
        }

        UsersEntity user = usersRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // Revoke all refresh tokens before deleting
        try {
            refreshTokenService.revokeAll(user);
        } catch (Exception e) {
            log.error("Failed to revoke tokens for user {}: {}", id, e.getMessage());
        }

        usersRepository.deleteById(id);
        log.info("User deleted: {}", user.getEmail());
    }

    @Transactional
    public void changePassword(String principal, ChangePasswordRequest req) {
        // Find user
        UsersEntity user = findUserByPrincipal(principal);

        // Validate new password
        validatePassword(req.getNewPassword());

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password and confirm password do not match");
        }

        // Verify old password (unless forced password change)
        if (!user.isForcePasswordChange()) {
            if (req.getOldPassword() == null ||
                    !encoder.matches(req.getOldPassword(), user.getPassword())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
            }
        }

        // Update password
        user.setPassword(encoder.encode(req.getNewPassword()));
        user.setForcePasswordChange(false);
        usersRepository.save(user);

        // Revoke all refresh tokens for security
        try {
            refreshTokenService.revokeAll(user);
            log.info("Password changed and all sessions revoked for user: {}", user.getEmail());
        } catch (Exception e) {
            log.error("Failed to revoke tokens after password change: {}", e.getMessage());
        }
    }

    // ========== AUTHENTICATION ==========

    public record TokensResult(String accessToken, String refreshToken) {}

    // Login attempt tracking methods
    private boolean isAccountLocked(String email) {
        LocalDateTime lockTime = lockoutTime.get(email);
        if (lockTime == null) return false;

        if (LocalDateTime.now().isAfter(lockTime.plusMinutes(LOCKOUT_DURATION_MINUTES))) {
            // Lockout expired, reset
            lockoutTime.remove(email);
            loginAttempts.remove(email);
            return false;
        }
        return true;
    }

    private void recordFailedLogin(String email) {
        int attempts = loginAttempts.getOrDefault(email, 0) + 1;
        loginAttempts.put(email, attempts);

        if (attempts >= MAX_LOGIN_ATTEMPTS) {
            lockoutTime.put(email, LocalDateTime.now());
            log.warn("Account locked due to too many failed attempts: {}", email);
        }
    }

    private void resetLoginAttempts(String email) {
        loginAttempts.remove(email);
        lockoutTime.remove(email);
    }

    @Transactional
    public TokensResult loginIssueTokens(LoginRequest req) {
        validateEmail(req.email());

        // Check if account is locked
        if (isAccountLocked(req.email())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Account temporarily locked due to too many failed login attempts. Try again in " +
                            LOCKOUT_DURATION_MINUTES + " minutes.");
        }

        // Find user
        UsersEntity user = usersRepository.findByEmail(req.email())
                .orElseThrow(() -> {
                    recordFailedLogin(req.email());
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
                });

        // Check account status (optional - uncomment if needed)
        // if (user.getStatus() == UserStatus.SUSPENDED) {
        //     throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        // }

        // Verify password
        if (!encoder.matches(req.password(), user.getPassword())) {
            recordFailedLogin(req.email());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        // Reset failed attempts on successful login
        resetLoginAttempts(req.email());

        // Generate tokens
        UUID jti = UUID.randomUUID();
        String accessToken = jwtService.generateAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                Collections.singleton(user.getRole()).toString()
        );
        String refreshToken = jwtService.generateRefreshToken(user.getEmail(), jti);

        refreshTokenService.create(user, refreshToken);

        log.info("User logged in: {}", user.getEmail());
        return new TokensResult(accessToken, refreshToken);
    }

    @Transactional
    public TokensResult refreshByToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh token provided");
        }

        // Validate token type
        if (!jwtService.isRefreshToken(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token type");
        }

        // Check if token exists and is valid
        if (!refreshTokenService.existsValid(refreshToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Refresh token expired or revoked");
        }

        // Get user from token
        String email = jwtService.validateTokenAndGetUsername(refreshToken);
        UsersEntity user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User not found"));

        // Check user status (optional - uncomment if needed)
        // if (user.getStatus() == UserStatus.SUSPENDED) {
        //     throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account is suspended");
        // }

        // Generate new tokens
        UUID newJti = UUID.randomUUID();
        String newAccessToken = jwtService.generateAccessToken(
                user.getUserId(),
                user.getEmail(),
                user.getUsername(),
                Collections.singleton(user.getRole()).toString()
        );
        String newRefreshToken = jwtService.generateRefreshToken(email, newJti);

        // Rotate refresh token
        refreshTokenService.rotate(user, refreshToken, newRefreshToken);

        log.debug("Tokens refreshed for user: {}", email);
        return new TokensResult(newAccessToken, newRefreshToken);
    }

    @Transactional
    public void logoutCurrent(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return; // Idempotent
        }

        try {
            refreshTokenService.revoke(refreshToken);
            String email = jwtService.getSubjectEvenIfExpired(refreshToken);
            log.info("User logged out (current device): {}", email);
        } catch (Exception e) {
            log.error("Failed to revoke refresh token: {}", e.getMessage());
        }
    }

    @Transactional
    public void logoutAllDevices(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return; // Idempotent
        }

        try {
            String email = jwtService.getSubjectEvenIfExpired(refreshToken);
            UsersEntity user = usersRepository.findByEmail(email).orElse(null);
            if (user != null) {
                refreshTokenService.revokeAll(user);
                log.info("User logged out (all devices): {}", email);
            }
        } catch (Exception e) {
            log.error("Failed to revoke all tokens: {}", e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    private UsersEntity findUserByPrincipal(String principal) {
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
        return maybeUser.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserModel toModel(UsersEntity entity) {
        UserModel model = new UserModel();
        model.setId(entity.getUserId());
        model.setUsername(entity.getUsername());
        model.setEmail(entity.getEmail());
        model.setPhoneNumber(entity.getPhoneNumber());
        model.setRole(entity.getRole());
        model.setStatus(entity.getStatus() != null ? entity.getStatus().name() : null);
        model.setCreatedAt(entity.getCreatedAt());
        return model;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return "USER";
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) role = role.substring(5);
        return "ADMIN".equals(role) ? "ADMIN" : "USER";
    }
}