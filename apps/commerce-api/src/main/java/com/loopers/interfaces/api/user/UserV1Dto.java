package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public class UserV1Dto {
    public record CreateUserRequest(
            @NotBlank
            @Pattern(
                    regexp = "^[A-Za-z0-9]+$",
                    message = "로그인 ID는 영문과 숫자만 사용할 수 있습니다."
            )
            String loginId,

            @NotBlank
            @Pattern(
                    regexp = "^[A-Za-z0-9!@#$%^&*()]{8,16}$",
                    message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다."
            )
            String password,

            @NotBlank
            String name,

            @NotNull
            LocalDate birthDate,

            @NotBlank
            @Email
            String email
    ) {}

    public record ChangePasswordRequest(
            @NotBlank
            String currentPassword,

            @NotBlank
            @Pattern(
                    regexp = "^[A-Za-z0-9!@#$%^&*()]{8,16}$",
                    message = "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다."
            )
            String newPassword
    ) {}

    public record UserResponse(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        public static UserResponse from(UserInfo userInfo) {
            return new UserResponse(
                    userInfo.loginId(),
                    userInfo.name(),
                    userInfo.birthDate(),
                    userInfo.email()
            );
        }

        public static UserResponse forMyInfo(UserInfo userInfo) {
            return new UserResponse(
                    userInfo.loginId(),
                    maskLastChar(userInfo.name()),
                    userInfo.birthDate(),
                    userInfo.email()
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
