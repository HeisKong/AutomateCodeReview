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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
        u.setRole(normalizeRole("user"));

        usersRepository.save(u);
    }


    private String normalizeRole(String role) {
        if (role == null) return "USER";
        role = role.trim().toUpperCase();
        if (role.startsWith("ROLE_")) role = role.substring(5);
        return "ADMIN".equals(role) ? "ADMIN" : "USER";
    }
}
