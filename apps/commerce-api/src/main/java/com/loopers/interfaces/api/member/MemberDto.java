package com.loopers.interfaces.api.member;

public class MemberDto {
    public record CreateMemberRequest(
            String userId,
            String password,
            String email,
            String username
    ){}
}
