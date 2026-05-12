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
}
