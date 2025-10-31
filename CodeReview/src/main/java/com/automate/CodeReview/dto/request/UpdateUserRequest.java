package com.automate.CodeReview.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
public class UpdateUserRequest {
    private UUID id;
    private String username;
    private String email;
    private String phoneNumber;
    private String role;
    private LocalDateTime createdAt;

}
