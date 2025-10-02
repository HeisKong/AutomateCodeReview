package com.automate.CodeReview.Service;

import com.automate.CodeReview.Models.LoginRequest;
import com.automate.CodeReview.Models.RegisterRequest;
import com.automate.CodeReview.Models.UserModel;
import com.automate.CodeReview.Response.LoginResponse;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import com.automate.CodeReview.Service.JwtService;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private String normalizeRole(String role) {
        if (role == null) return "USER";
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) role = role.substring(5);
        return "ADMIN".equals(role) ? "ADMIN" : "USER";
    }
}

