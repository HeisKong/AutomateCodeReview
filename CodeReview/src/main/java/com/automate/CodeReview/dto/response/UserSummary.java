package com.automate.CodeReview.dto.response;

import com.automate.CodeReview.entity.UserStatus;

public record UserSummary(
        String username,
        String email,
        UserStatus status,
        String phoneNumber
) {}
