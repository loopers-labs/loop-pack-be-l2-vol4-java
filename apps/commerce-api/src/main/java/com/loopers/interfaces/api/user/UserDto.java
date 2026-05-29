package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserDto {

    public record UserResponse(Long id, String loginId) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.id(), info.loginId());
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}
}
