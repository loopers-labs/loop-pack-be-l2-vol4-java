package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record UserJoinRequest(
            String loginId,
            String loginPassword,
            String name,
            LocalDate birthday,
            String email
    ) {
        public UserCommand.Join toCommand() {
            return new UserCommand.Join(loginId, loginPassword, name, birthday, email);
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthday,
            String email
    ) {
        public static UserResponse from(UserInfo userInfo) {
            return new UserResponse(
                    userInfo.loginId(),
                    userInfo.name(),
                    userInfo.birthday(),
                    userInfo.email()
            );
        }
    }
}
