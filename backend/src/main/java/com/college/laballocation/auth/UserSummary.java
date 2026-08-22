package com.college.laballocation.auth;

import com.college.laballocation.user.AppUser;

/** Safe, public-facing user profile - never includes password/passwordHash or any internal field. */
public record UserSummary(Long id, String email, String displayName, String role) {

    public static UserSummary from(AppUser user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name());
    }
}
