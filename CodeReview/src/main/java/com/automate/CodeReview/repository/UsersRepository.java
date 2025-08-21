package com.automate.CodeReview.repository;

import com.automate.CodeReview.entity.UsersEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository // จะใส่หรือไม่ใส่ก็ได้ แต่ช่วยให้อ่านง่าย
public interface UsersRepository extends JpaRepository<UsersEntity, UUID> {
    Optional<UsersEntity> findByUsername(String username);
    Optional<UsersEntity> findById(UUID id);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

}
