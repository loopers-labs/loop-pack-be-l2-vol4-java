package com.loopers.interfaces.api.user;

import java.time.LocalDate;

import com.loopers.application.user.UserSignUpInfo;

public class UserV1Dto {

    public record SignUpRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
    }

    public record SignUpResponse(Long userId, String loginId) {

        public static SignUpResponse from(UserSignUpInfo userSignUpInfo) {
            return new SignUpResponse(userSignUpInfo.userId(), userSignUpInfo.loginId());
        }
    }
}
