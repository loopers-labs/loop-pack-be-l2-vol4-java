package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;

public class UserV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        String birth,
        String email
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
}
