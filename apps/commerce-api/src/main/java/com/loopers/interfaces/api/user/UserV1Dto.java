package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {}

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}

    public record UserResponse(String loginId, String name, String birthDate, String email) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
