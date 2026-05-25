package com.loopers.domain.user;

import java.time.LocalDate;

public record PasswordValidationContext(String rawPassword, LocalDate birthDate) {

    public static PasswordValidationContext from(String rawPassword, UserModel user) {
        return new PasswordValidationContext(rawPassword, user.getBirthDate());
    }
}
