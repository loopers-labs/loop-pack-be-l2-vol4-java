package com.loopers.interfaces.api.member;

import java.time.LocalDate;

public class MemberRequest {

    public record SignUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
    }

    public record UpdatePassword(
            String oldPassword,
            String newPassword
    ) {
    }
}
