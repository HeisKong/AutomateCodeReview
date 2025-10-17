package com.automate.CodeReview.Models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserModel {
    private UUID id;
    private String username;
    @JsonIgnore
    private String email;
    @JsonIgnore
    private String phoneNumber;
    @JsonIgnore
    private String role;
    @JsonIgnore
    private String password;
    @JsonIgnore
    private LocalDateTime createdAt;

}
