package com.automate.CodeReview.dto.request;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class UpdateUserProfileRequest {
    private String username;
    private String email;
    private String phoneNumber;

}
