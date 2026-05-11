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

    public record MyInfoResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.loginId(),
                maskLastChar(info.name()),
                info.birthDate(),
                info.email()
            );
        }
    }

    private static String maskLastChar(String value) {
        if (value.length() <= 1) {
            return "*";
        }
        return value.substring(0, value.length() - 1) + "*";
    }
}
