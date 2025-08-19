package com.automate.CodeReview.Response;

import com.automate.CodeReview.Models.UserModel;

public class LoginResponse {
    private String token;
    private UserModel user;

    public LoginResponse(String token, UserModel user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserModel getUser() {
        return user;
    }

    public void setUser(UserModel user) {
        this.user = user;
    }
}
