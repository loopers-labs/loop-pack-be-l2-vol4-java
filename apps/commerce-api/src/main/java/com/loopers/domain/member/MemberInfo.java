package com.loopers.domain.member;

import java.time.LocalDate;

public record MemberInfo(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
}
