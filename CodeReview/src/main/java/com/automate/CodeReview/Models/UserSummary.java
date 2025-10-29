package com.automate.CodeReview.Models;

import com.automate.CodeReview.entity.UserStatus;

public record UserSummary(
        String username,
        String email,
        UserStatus status,
        String phoneNumber
) {}
