package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignUpRequest(
            String loginId,
            String password,
            String name,
            LocalDate birthday,
            String email
    ) {}

    public record ChangePasswordRequest(
            String newPassword
    ) {}

    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthday,
            String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                    info.loginId(),
                    info.name(),
                    info.birthday(),
                    info.email()
            );
        }
    }
}
