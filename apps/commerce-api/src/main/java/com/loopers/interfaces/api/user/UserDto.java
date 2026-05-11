package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserCommand;
import com.loopers.application.user.UserInfo;

import java.time.LocalDate;

public class UserDto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public UserCommand.SignUp toCommand() {
            return new UserCommand.SignUp(loginId, password, name, birthDate, email);
        }
    }

    public record MeResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MeResponse from(UserInfo info) {
            return new MeResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {
    }

    private UserDto() {
    }
}
