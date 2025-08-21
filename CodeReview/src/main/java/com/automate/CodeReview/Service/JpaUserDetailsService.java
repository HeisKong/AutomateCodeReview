package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.UsersRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final UsersRepository usersRepository;

    public JpaUserDetailsService(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        UsersEntity u = usersRepository.findByEmail(email) // เปลี่ยนเป็น email
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        String role = u.getRole();
        if (role != null && role.startsWith("ROLE_")) {
            role = role.substring(5);
        }

        return User.withUsername(u.getEmail())  // ใช้ email เป็น username
                .password(u.getPassword())      // ต้องเป็น BCrypt hash
                .roles(role == null ? "USER" : role)
                .build();
    }
}
