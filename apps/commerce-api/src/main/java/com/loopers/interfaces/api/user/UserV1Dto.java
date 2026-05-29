package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.NotBlank;

public class UserV1Dto {

    public record RegisterRequest(
        @NotBlank(message = "아이디는 빈값이 들어올 수 없습니다.") String userId,
        @NotBlank(message = "비밀번호는 빈값이 들어올 수 없습니다.") String password,
        @NotBlank(message = "이름은 빈값이 들어올 수 없습니다.") String name,
        @NotBlank(message = "생년월일은 빈값이 들어올 수 없습니다.") String birthDay,
        @NotBlank(message = "이메일은 빈값이 들어올 수 없습니다.") String email
    ) {}

    public record ChangePasswordRequest(
        @NotBlank(message = "비밀번호는 빈값이 들어올 수 없습니다.") String newPassword
    ) {}

    public record UserResponse(
        Long id,
        String userId,
        String name,
        String email,
        String birthDay
    ) {
        public static UserResponse from(UserInfo info) {
            return new UserResponse(
                info.id(),
                info.userId(),
                info.maskedName(),
                info.email(),
                info.birthDay()
            );
        }
    }
}
