package com.loopers.interfaces.api.user;

import java.time.LocalDate;

import com.loopers.application.user.UserMyInfo;
import com.loopers.application.user.UserSignUpInfo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class UserV1Dto {

    public record SignUpRequest(
        @NotBlank(message = "로그인 ID는 null이거나 빈 값일 수 없습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 null이거나 빈 값일 수 없습니다.")
        String password,

        @NotBlank(message = "이름은 null이거나 빈 값일 수 없습니다.")
        String name,

        @NotNull(message = "생년월일은 null일 수 없습니다.")
        LocalDate birthDate,

        @NotBlank(message = "이메일은 null이거나 빈 값일 수 없습니다.")
        String email
    ) {
    }

    public record SignUpResponse(Long userId, String loginId) {

        public static SignUpResponse from(UserSignUpInfo userSignUpInfo) {
            return new SignUpResponse(userSignUpInfo.userId(), userSignUpInfo.loginId());
        }
    }

    public record MyInfoResponse(String loginId, String name, LocalDate birthDate, String email) {

        public static MyInfoResponse from(UserMyInfo userMyInfo) {
            return new MyInfoResponse(
                userMyInfo.loginId(),
                userMyInfo.name(),
                userMyInfo.birthDate(),
                userMyInfo.email()
            );
        }
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호는 null이거나 빈 값일 수 없습니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 null이거나 빈 값일 수 없습니다.")
        String newPassword
    ) {
    }
}
