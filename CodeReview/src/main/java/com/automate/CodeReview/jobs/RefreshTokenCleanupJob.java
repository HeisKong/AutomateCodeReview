package com.automate.CodeReview.jobs;

import com.automate.CodeReview.Service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupJob.class);
    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupJob(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }
    /** 🔹 รันทันทีหลังจาก Spring สตาร์ท */
    @jakarta.annotation.PostConstruct
    public void purgeExpiredOnStartup() {
        long deleted = refreshTokenService.purgeExpired();
        log.info("Purged {} expired refresh tokens (on startup)", deleted);
    }
    /** รันทุกวันเวลา 02:00 (Asia/Bangkok) */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Bangkok")
    public void purgeExpiredDaily() {
        long deleted = refreshTokenService.purgeExpired();
        log.info("Purged {} expired refresh tokens", deleted);
    }
}
