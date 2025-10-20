package com.automate.CodeReview.Response;

import com.automate.CodeReview.Models.UserModel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String token;

    public LoginResponse(String token) {
        this.token = token;
    }
}
