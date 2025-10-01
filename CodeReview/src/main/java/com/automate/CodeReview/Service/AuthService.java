package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.LoginRequest;
import com.automate.CodeReview.Models.RegisterRequest;
import com.automate.CodeReview.Models.UpdateUserRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.SecureRandom.PasswordUtils;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import com.automate.CodeReview.Service.JwtService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final PasswordEncoder encoder;
    private final UsersRepository usersRepository;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authManager,
                       PasswordEncoder encoder,
                       UsersRepository usersRepository,
                       JwtService jwtService) {
        this.authManager = authManager;
        this.encoder = encoder;
        this.usersRepository = usersRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest req) {
        authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password())
        );


        UsersEntity u = usersRepository.findByEmail(req.email())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String token = jwtService.generateToken(u.getEmail());


        UserModel model = toModel(u);

        return new LoginResponse(token, model);
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
        if (usersRepository.existsByUsername(req.username())) {
            throw new DuplicateKeyException("Username already exists");
        }
        if (usersRepository.existsByEmail(req.email())) {
            throw new DuplicateKeyException("Email already exists");
        }
        if (usersRepository.existsByPhoneNumber(req.phoneNumber())) {
            throw new DuplicateKeyException("Phone number already exists");
        }

        UsersEntity u = new UsersEntity();
        u.setUsername(req.username());
        u.setEmail(req.email());
        u.setPassword(encoder.encode(req.password())); // BCrypt
        u.setPhoneNumber(req.phoneNumber());
        u.setRole(normalizeRole(req.role()));

        usersRepository.save(u);
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
    public String adminResetPassword(String email) {
        UsersEntity user = usersRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        // สร้าง temp password
        String tempPassword = PasswordUtils.generateTempPassword(12);

        // แฮชแล้วเซฟ
        user.setPassword(encoder.encode(tempPassword));
        user.setForcePasswordChange(true);
        usersRepository.save(user);

        // คืน temp password ใน response (เฉพาะ dev/test)
        return tempPassword;
    }

    private String normalizeRole(String role) {
        if (role == null) return "USER";
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) role = role.substring(5);
        return "ADMIN".equals(role) ? "ADMIN" : "USER";
    }
}
