package com.college.laballocation.user;

public final class UserDtos {
    private UserDtos() {}

    public record UserSummaryResponse(Long id, String email, String displayName, UserRole role, boolean active) {
        static UserSummaryResponse from(AppUser user) {
            return new UserSummaryResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(), user.isActive());
        }
    }
}
