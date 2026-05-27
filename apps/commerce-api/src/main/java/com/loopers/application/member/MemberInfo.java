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
                model.getName(),
                model.getBirthDate(),
                model.getEmail()
        );
    }

    public static MemberInfo fromMasked(MemberModel model) {
        return new MemberInfo(
                model.getLoginId(),
                maskName(model.getName()),
                model.getBirthDate(),
                model.getEmail()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
