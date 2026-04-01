package com.example.userservice.dto;

public class LoginResponse {
    private String token;
    private UserDTO userInfo;

    public LoginResponse() {
    }

    public LoginResponse(String token, UserDTO userInfo) {
        this.token = token;
        this.userInfo = userInfo;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserDTO getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserDTO userInfo) {
        this.userInfo = userInfo;
    }
}
