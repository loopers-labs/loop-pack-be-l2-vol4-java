package com.loopers.application.member;

import java.time.LocalDate;

public class MemberCommand {

    public record Join(
            String loginId,
            String loginPassword,
            String name,
            LocalDate birthday,
            String email
    ) {}

    public record GetMember(
            String loginId,
            String loginPassword
    ) {}

    public record ChangePassword(
            String loginId,
            String loginPassword,
            String currentPassword,
            String newPassword
    ) {}
}
