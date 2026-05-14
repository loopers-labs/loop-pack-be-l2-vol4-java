package com.loopers.domain.user;

public final class UserCommand {

    private UserCommand() {
    }

    public record SignUp(
        LoginId loginId,
        Name name,
        BirthDate birthDate,
        Email email,
        String rawPassword
    ) {
    }

    public record Authenticate(
        String loginIdInput,
        String rawPasswordInput
    ) {
    }
}
