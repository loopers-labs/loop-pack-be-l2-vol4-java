package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class UserDto {

    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 필수입니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @NotNull(message = "생년월일은 필수입니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 필수입니다.")
        String email
    ) {}

    public record SignUpResponse(String loginId) {
        public static SignUpResponse from(UserInfo info) {
            return new SignUpResponse(info.loginId());
        }
    }

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.maskedName(),
                info.birthDate(),
                info.email()
            );
        }
    }

    public record UpdatePasswordRequest(
        @NotBlank(message = "기존 비밀번호는 필수입니다.")
        String oldPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        String newPassword
    ) {}
}
