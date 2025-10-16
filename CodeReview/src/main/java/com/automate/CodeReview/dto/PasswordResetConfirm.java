// dto/PasswordResetConfirm.java
package com.automate.CodeReview.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirm(
        @NotBlank String token,
        @NotBlank String newPassword
) {}
