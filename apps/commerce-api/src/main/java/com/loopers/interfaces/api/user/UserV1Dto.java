package com.loopers.interfaces.api.user;

import java.time.LocalDate;

import com.loopers.application.user.UserMyInfo;
import com.loopers.application.user.UserSignUpInfo;

public class UserV1Dto {

    public record SignUpRequest(String loginId, String password, String name, LocalDate birthDate, String email) {
    }

    public record SignUpResponse(Long userId, String loginId) {

        public static SignUpResponse from(UserSignUpInfo userSignUpInfo) {
            return new SignUpResponse(userSignUpInfo.userId(), userSignUpInfo.loginId());
        }
    }

    public record MyInfoResponse(String loginId, String name, LocalDate birthDate, String email) {

        public static MyInfoResponse from(UserMyInfo userMyInfo) {
            return new MyInfoResponse(
                userMyInfo.loginId(),
                userMyInfo.name(),
                userMyInfo.birthDate(),
                userMyInfo.email()
            );
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}
