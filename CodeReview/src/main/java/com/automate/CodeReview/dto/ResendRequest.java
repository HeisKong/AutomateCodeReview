package com.automate.CodeReview.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendRequest(@NotBlank @Email String email) {}
