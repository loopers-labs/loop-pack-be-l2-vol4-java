package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public class UserV1Dto {
    public record SignupRequest(
        @Schema(example = "usertest123") String userId,
        @Schema(example = "abc123!@#") String password,
        @Schema(example = "홍길동") String name,
        @Schema(example = "1995-06-10") LocalDate birthDate,
        @Schema(example = "test@naver.com") String email
    ) {}

    public record ChangePasswordRequest(
        @Schema(example = "abc123!@#") String currentPassword,
        @Schema(example = "newPass99@") String newPassword
    ) {}

    public record UserResponse(
        String userId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.userId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
