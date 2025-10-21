package com.automate.CodeReview.Service;

import com.automate.CodeReview.entity.RefreshToken;
import com.automate.CodeReview.entity.UsersEntity;
import com.automate.CodeReview.repository.RefreshTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository repo, JwtService jwtService) {
        this.repo = repo;
        this.jwtService = jwtService;
    }

    /* ========================= สร้าง/บันทึก ========================= */

    /**
     * สร้างเรคคอร์ด refresh token ใน DB จาก JWT ที่เพิ่งออก
     * - จะอ่านวันหมดอายุจาก exp ใน JWT เพื่อให้ตรงกับตัว token จริง
     */
    public RefreshToken create(UsersEntity user, String refreshJwt) {
        if(user == null||refreshJwt == null||refreshJwt.isBlank()){
            throw new IllegalArgumentException("User and refresh token must not be null");
        }
        Date exp = jwtService.parseAllClaims(refreshJwt).getExpiration();
        if(exp == null){
            throw new IllegalArgumentException("Refresh token must have expiration date");
        }
        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setToken(refreshJwt);
        rt.setExpiryDate(exp.toInstant());
        return repo.save(rt);
    }

    /* ========================= ค้นหา/ตรวจสอบ ========================= */

    public Optional<RefreshToken> findByToken(String token) {
        return repo.findByToken(token);
    }

    public boolean isExpired(RefreshToken rt) {
        if(rt == null) return true;
        return rt.getExpiryDate() == null || rt.getExpiryDate().isBefore(Instant.now());
    }

    /** ตรวจสอบว่า token ที่ให้มา "ยังอยู่ใน DB และยังไม่หมดอายุ" */
    public boolean existsValid(String token) {
        return findByToken(token).filter(rt -> !isExpired(rt)).isPresent();
    }

    /* ========================= หมุน/เพิกถอน ========================= */

    /**
     * หมุน (rotate) refresh token:
     * - ลบเรคคอร์ดตัวเก่าตามค่า oldToken
     * - สร้างเรคคอร์ดใหม่จาก newToken
     * ทำงานในทรานแซกชันเดียวเพื่อความสม่ำเสมอ
     */
    @Transactional
    public RefreshToken rotate(UsersEntity user, String oldToken, String newToken) {
        if (oldToken == null || oldToken.isBlank()) {
            throw new IllegalArgumentException("Old token must not be null");
        }
        if (newToken == null || newToken.isBlank()) {
            throw new IllegalArgumentException("New token must not be null");
        }
        repo.deleteByToken(oldToken);
        return create(user, newToken);
    }

    /** เพิกถอน refresh token ปัจจุบัน (เช่นเวลา logout เครื่องนี้) */
    @Transactional
    public void revoke(String token) {
        if(token == null||token.isBlank()) return;
        repo.deleteByToken(token);
    }

    /** เพิกถอน refresh token ทั้งหมดของผู้ใช้ (logout all devices) */
    @Transactional
    public long revokeAll(UsersEntity user) {
        if (user == null) return 0;
        return repo.deleteByUser(user);
    }

    /* ========================= งานดูแลเป็นครั้งคราว ========================= */

    /**
     * ลบ token ที่หมดอายุทิ้ง (ควรเรียกจาก scheduler รายวัน)
     * ต้องมีเมธอดใน Repository: long deleteByExpiryDateBefore(Instant now);
     */
    @Transactional
    public long purgeExpired() {
        return repo.deleteByExpiryDateBefore(Instant.now());
    }

}
