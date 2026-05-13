package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public class UserV1Dto {
    public record CreateUserRequest(
        @NotBlank String loginId,
        @NotBlank String name,
        @NotNull @Past LocalDate birth,
        @NotBlank @Pattern(
            regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$",
            message = "비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다"
        ) String password,
        @NotBlank @Email String email
    ) {}

    public record ChangeUserPasswordRequest(
        @NotBlank @Pattern(
            regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$",
            message = "비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다"
        ) String oldPassword,
        @NotBlank @Pattern(
            regexp = "^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]{8,16}$",
            message = "비밀번호 생성 규칙 위반 : 8 ~ 16자의 영문 대소문자, 숫자, 특수문자만 가능합니다"
        ) String targetPassword
    ) {}

    public record UserResponse(
        String loginId,
        String name,
        LocalDate birth,
        String email
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.loginId(),
                info.name(),
                info.birthVO().date(),
                info.emailVO().email()
            );
        }
    }
}
