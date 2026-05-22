package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;

public class MemberV1Dto {

    public record RegisterRequest(
        String loginId,
        String password,
        String name,
        String birthDate,
        String email
    ) {}

    public record ChangePasswordRequest(
        String oldPassword,
        String newPassword
    ) {}

    public record MemberResponse(
        String loginId,
        String name,
        String birthDate,
        String email
    ) {
        public static MemberResponse from(MemberInfo info) {
            return new MemberResponse(
                info.loginId(),
                info.name(),
                info.birthDate(),
                info.email()
            );
        }
    }
}
