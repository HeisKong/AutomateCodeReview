
package com.automate.CodeReview.dto.response;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirm(
        @NotBlank String token,
        @NotBlank String newPassword
) {}
