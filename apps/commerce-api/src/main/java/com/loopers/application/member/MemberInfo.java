package com.loopers.application.member;

import com.loopers.domain.member.Member;

import java.time.LocalDate;

public record MemberInfo(String loginId,
                         String name,
                         LocalDate birthday,
                         String email) {
    public static MemberInfo from(Member member) {
        return new MemberInfo(
                member.getLoginId(),
                member.getName(),
                member.getBirthday(),
                member.getEmail());
    }


    public static MemberInfo fromWithMaskedName(Member member) {
        return new MemberInfo(
                member.getLoginId(),
                maskName(member.getName()),
                member.getBirthday(),
                member.getEmail());
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) return name;
        return name.substring(0, name.length() - 1) + "*";
    }
}
