package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.Gender;
import jakarta.validation.constraints.NotNull;

public class UserV1Dto {

    public record SignUpRequest(
            @NotNull String loginId,
            @NotNull String password,
            @NotNull String name,
            @NotNull String birthDate,
            @NotNull String email,
            @NotNull Gender gender
    ) {
    }

    public record UserResponse(Long id, String loginId, String name, String birthDate, String email) {

        public static UserResponse from(UserInfo info) {
            return new UserResponse(info.id(), info.loginId(), info.name(), info.birthDate(), info.email());
        }

        public static UserResponse fromMasked(UserInfo info) {
            String name = info.name();
            String masked = name.substring(0, name.length() - 1) + "*";
            return new UserResponse(info.id(), info.loginId(), masked, info.birthDate(), info.email());
        }
    }

}
