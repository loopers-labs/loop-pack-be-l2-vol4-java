package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
        String loginId,
        String name,
        LocalDate birthday,
        String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
                user.getLoginId(),
                user.getName(),
                user.getBirthday(),
                user.getEmail()
        );
    }

    public static UserInfo fromWithMaskedName(User user) {
        return new UserInfo(
                user.getLoginId(),
                maskName(user.getName()),
                user.getBirthday(),
                user.getEmail()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) return name;
        return name.substring(0, name.length() - 1) + "*";
    }
}
