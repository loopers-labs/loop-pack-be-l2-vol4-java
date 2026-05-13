package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.Gender;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        String email,
        String birthDate,
        Gender gender
    ) {}

    public record UserResponse(Long id, String loginId, String name, String email, String birthDate, Gender gender) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.email(),
                info.birthDate(),
                info.gender()
            );
        }
    }

    public record MyInfoResponse(String loginId, String name, String email, String birthDate) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                info.maskedName(),
                info.email(),
                info.birthDate()
            );
        }
    }
}
