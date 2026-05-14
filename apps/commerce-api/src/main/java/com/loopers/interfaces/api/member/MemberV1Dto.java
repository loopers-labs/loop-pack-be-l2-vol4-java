package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;

public class MemberV1Dto {
    public record CreateMemberRequest(
            String userId,
            String password,
            String email,
            String userName,
            String birthDate
    ){}

    public record CreateMemberResponse(
            String userId,
            String userName
    ){
        public static CreateMemberResponse from(MemberInfo info) {
            return new CreateMemberResponse(info.userId(), info.userName());
        }
    }

    public record MemberInfoResponse(
            String userId,
            String userName,
            String birthDate,
            String email
    ){
        public static MemberInfoResponse from(MemberInfo info) {
            return new MemberInfoResponse(info.userId(), info.maskedUserName(), info.birthDate(), info.email());
        }
    }

    public record UpdatePasswordRequest(
            String currentPassword,
            String newPassword
    ){}
}
