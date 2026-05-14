package com.loopers.interfaces.api.member;

import java.time.LocalDate;

public class MemberResponse {

    public record Info(
            String loginId,
            String name,
            LocalDate birthDate,
            String email
    ) {
    }
}
