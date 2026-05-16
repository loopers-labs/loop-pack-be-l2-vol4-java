package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

public record MemberInfo(
        String userId,
        String userName,
        String maskedUserName,
        String email,
        String birthDate
) {
    public static MemberInfo from(MemberModel member) {
        return new MemberInfo(
                member.getUserId(),
                member.getUsername(),
                member.getMaskedUsername(),
                member.getEmail(),
                member.getBirthDate()
        );
    }
}
