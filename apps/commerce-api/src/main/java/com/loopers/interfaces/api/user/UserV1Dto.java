package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import jakarta.validation.constraints.NotBlank;

public class UserV1Dto {

    public record RegisterRequest(
        @NotBlank String loginId,
        @NotBlank String password,
        @NotBlank String name,
        @NotBlank String birth,
        @NotBlank String email
    ) {}

    // password 필드 의도적으로 제외 — 사이클 9 검증 대상
    public record RegisterResponse(
        Long id,
        String loginId,
        String name,
        String birth,
        String email
    ) {
        public static RegisterResponse from(UserInfo info) {
            return new RegisterResponse(
                info.id(),
                info.loginId(),
                info.name(),
                info.birth(),
                info.email()
            );
        }
    }

    // 내정보 조회 응답 — name 은 마스킹된 값
    public record MyInfoResponse(
        Long id,
        String loginId,
        String name,
        String birth,
        String email
    ) {
        public static MyInfoResponse from(UserInfo info) {
            return new MyInfoResponse(
                info.id(),
                info.loginId(),
                info.maskedName(),
                info.birth(),
                info.email()
            );
        }
    }

    // 비밀번호 변경 요청
    public record ChangePasswordRequest(
        String currentPassword,
        String newPassword
    ) {}
}
