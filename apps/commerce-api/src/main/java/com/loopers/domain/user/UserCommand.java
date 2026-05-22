package com.loopers.domain.user;

import java.time.LocalDate;

public class UserCommand {

    public record SignUp(
        String loginId,
        String password,
        String name,
        LocalDate birthDate,
        String email
    ) {
    }
}
