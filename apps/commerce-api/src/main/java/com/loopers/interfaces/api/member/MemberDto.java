package com.loopers.interfaces.api.member;

public class MemberDto {
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
    ){}

    public record MemberInfoResponse(
            String userId,
            String userName,
            String birthDate,
            String email
    ){}

    public record UpdatePasswordRequest(
            String currentPassword,
            String newPassword
    ){}
}
