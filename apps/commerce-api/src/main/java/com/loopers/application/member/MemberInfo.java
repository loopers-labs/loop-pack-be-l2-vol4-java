package com.loopers.application.member;

import com.loopers.domain.member.Member;

public record MemberInfo(String loginId, String name, String birthDate, String email) {

    public static MemberInfo from(Member member) {
        return new MemberInfo(
            member.getLoginId(),
            member.getName(),
            member.getBirthDate(),
            member.getEmail()
        );
    }
}
