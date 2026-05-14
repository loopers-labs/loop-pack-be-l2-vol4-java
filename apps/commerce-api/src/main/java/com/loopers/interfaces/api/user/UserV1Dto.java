package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
public class UserV1Dto {

    public record RegisterRequest(
        String userid,
        String password,
        String name,
        String birthDay,
        String email
    ) {}

    public record ChangePasswordRequest(
        String newPassword
    ) {}

    public record UserResponse(
        Long id,
        String userid,
        String name,
        String email,
        String birthDay
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.userid(),
                info.maskedName(),
                info.email(),
                info.birthDay()
            );
        }
    }
}
