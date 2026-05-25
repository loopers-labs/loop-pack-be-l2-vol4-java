package com.loopers.application.user;

import java.time.LocalDate;

public class UserCommand {

    public record Join(
            String loginId,
            String loginPassword,
            String name,
            LocalDate birthday,
            String email
    ) {}

    public record GetUser(
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
