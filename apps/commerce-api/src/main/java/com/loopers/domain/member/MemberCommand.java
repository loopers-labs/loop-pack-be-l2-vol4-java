package com.loopers.domain.member;

import java.time.LocalDate;

public class MemberCommand {

    public record SignUp(
            String loginId,
            String password,
            String name,
            LocalDate birthDate,
            String email
    ) {
    }

    public record UpdatePassword(
            String loginId,
            String currentPassword,
            String oldPassword,
            String newPassword
    ) {
    }
}
