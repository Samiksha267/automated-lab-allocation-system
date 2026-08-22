package com.college.laballocation.auth;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    public static LoginResponse of(String accessToken, long expiresInSeconds, UserSummary user) {
        return new LoginResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
