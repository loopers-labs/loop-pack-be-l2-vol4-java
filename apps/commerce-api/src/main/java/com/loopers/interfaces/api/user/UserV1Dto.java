package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {
    public record SignupRequest (
            String userId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {}

    public record ChangePasswordRequest (
            String currentPassword,
            String newPassword
    ) {}

    public record UserResponse (
            String userId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                    info.userId(),
                    info.name(),
                    info.birthDate(),
                    info.email()
            );
        }
    }
}
