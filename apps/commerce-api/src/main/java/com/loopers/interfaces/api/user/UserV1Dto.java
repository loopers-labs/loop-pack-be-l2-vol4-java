package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public UserCommand.SignUp toCommand() {
            return new UserCommand.SignUp(
                loginId,
                password,
                name,
                birthDate,
                email
            );
        }
    }

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo.User info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {
        public UserCommand.ChangePassword toCommand() {
            return new UserCommand.ChangePassword(currentPassword, newPassword);
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MyInfoResponse from(UserInfo.User info) {
            return new MyInfoResponse(
                info.loginId(),
                maskLastCharacter(info.name()),
                info.birthDate(),
                info.email()
            );
        }

        private static String maskLastCharacter(String name) {
            return name.substring(0, name.length() - 1) + "*";
        }
    }
}
