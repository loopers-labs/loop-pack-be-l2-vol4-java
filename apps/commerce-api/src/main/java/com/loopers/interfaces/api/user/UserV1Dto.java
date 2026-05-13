package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {
    }

    public record UpdatePasswordRequest(
        String oldPassword,
        String newPassword
    ) {
    }

    public record SignUpResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static SignUpResponse from(UserInfo info) {
            return new SignUpResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                maskLastChar(info.name()),
                info.birthDate(),
                info.email()
            );
        }

        private static String maskLastChar(String value) {
            if (value == null || value.isEmpty()) {
                return value;
            }
            return value.substring(0, value.length() - 1) + "*";
        }
    }
}
