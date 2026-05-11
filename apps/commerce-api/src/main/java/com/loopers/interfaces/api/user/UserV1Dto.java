package com.loopers.interfaces.api.user;

import com.loopers.application.user.SignUpCommand;
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
        public SignUpCommand toCommand() {
            return new SignUpCommand(loginId, password, name, birthDate, email);
        }
    }

    public record UserResponse(
        Long id,
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
