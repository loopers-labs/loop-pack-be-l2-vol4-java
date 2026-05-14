package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;

import java.time.LocalDate;

public record MemberInfo(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static MemberInfo from(MemberModel model) {
        return new MemberInfo(
                model.getLoginId(),
                model.getMaskedName(),
                model.getBirthDate(),
                model.getEmail()
        );
    }

    public static MemberInfo of(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
        return new MemberInfo(
                loginId,
                name,
                birthDate,
                email
        );
    }
}
