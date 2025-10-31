package com.automate.CodeReview.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;


@Data
public class RepositoryCreateRequest {
    @NotNull
    private UUID user;
    @NotBlank
    private String name;
    @NotBlank
    private String repositoryUrl;
    private String projectType;

//     one-time credentials
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
}
