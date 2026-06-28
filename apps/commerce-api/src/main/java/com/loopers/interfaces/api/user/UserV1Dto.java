package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record RegisterRequest(
        String loginId,
        String loginPw,
        String email,
        String nickname,
        LocalDate birthDate
    ) {}

    public record ChangePasswordRequest(String oldPassword, String newPassword) {}

    public record UserResponse(
        Long id,
        String loginId,
        String email,
        String nickname,
        LocalDate birthDate
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.email(),
                info.nickname(),
                info.birthDate()
            );
        }
    }
}
