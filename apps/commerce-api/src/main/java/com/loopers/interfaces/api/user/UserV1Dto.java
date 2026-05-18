package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignupRequest(
        String loginId,
        String password,
        String name,
        LocalDate birth,
        String email
    ) {}

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birth,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birth(),
                info.email()
            );
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        LocalDate birth,
        String email
    ) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                info.name(),
                info.birth(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}
}
