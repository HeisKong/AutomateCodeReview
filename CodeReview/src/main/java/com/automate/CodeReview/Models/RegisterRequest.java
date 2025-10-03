package com.automate.CodeReview.Models;

import jakarta.validation.constraints.NotBlank;

public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String email,
        @NotBlank String phoneNumber,
        @NotBlank String role
) {
}