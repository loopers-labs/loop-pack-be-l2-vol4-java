package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberCommand;
import com.loopers.application.member.MemberInfo;

import java.time.LocalDate;

public class MemberV1Dto {
    public record MemberJoinRequest(
            String loginId,
            String loginPassword,
            String name,
            LocalDate birthday,
            String email
    ) {
        public MemberCommand.Join toCommand() {
            return new MemberCommand.Join(loginId, loginPassword, name, birthday, email);
        }
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record MemberResponse(
            String loginId,
            String name,
            LocalDate birthday,
            String email
    ) {
        public static MemberResponse from(MemberInfo memberInfo) {
            return new MemberResponse(
                    memberInfo.loginId(),
                    memberInfo.name(),
                    memberInfo.birthday(),
                    memberInfo.email());
        }
    }
}
