package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UserRegistrationCommand;

public class UserV1Dto {

    public record UserResponse(String loginId, String name, String birthDate, String email) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.loginId(), info.name(), info.birthDate(), info.email());
        }
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {}

    public record UserRegisterRequest(String loginId, String password, String name, String birthDate, String email) {
        public UserRegistrationCommand toCommand() {
            return new UserRegistrationCommand(loginId, password, name, birthDate, email);
        }
    }
}
