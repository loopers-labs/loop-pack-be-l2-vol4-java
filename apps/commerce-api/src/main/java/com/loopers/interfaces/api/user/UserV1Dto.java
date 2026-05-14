package com.loopers.interfaces.api.user;

import com.loopers.application.user.SignUpCommand;
import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record SignUpRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email,
        String gender
    ) {
        public SignUpCommand toCommand() {
            return new SignUpCommand(loginId, password, name, birthDate, email, gender);
        }
    }

    public record SignUpResponse(
        Long id,
        String loginId,
        String name,
        String birthDate,
        String email,
        String gender
    ) {
        public static SignUpResponse from(UserInfo info) {
            return new SignUpResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email(),
                info.gender()
            );
        }
    }

    public record MyInfoResponse(
        String loginId,
        String name,
        String birthDate,
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

        private static String maskLastChar(String name) {
            if (name == null || name.isEmpty()) {
                return name;
            }
            if (name.length() == 1) {
                return "*";
            }
            return name.substring(0, name.length() - 1) + "*";
        }
    }
}
