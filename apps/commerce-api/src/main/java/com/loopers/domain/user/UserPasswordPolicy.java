package com.loopers.domain.user;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserPasswordPolicy {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");

    public static boolean containsBirthDate(String password, LocalDate birthDate) {
        if (password == null || birthDate == null) {
            return false;
        }
        return password.contains(birthDate.format(YYYYMMDD))
            || password.contains(birthDate.format(YYMMDD));
    }
}
