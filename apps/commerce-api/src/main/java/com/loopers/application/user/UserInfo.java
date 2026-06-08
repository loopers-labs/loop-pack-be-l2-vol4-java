package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
                model.getLoginId(),
                model.getName(),
                model.getBirthDate(),
                model.getEmail()
        );
    }

    public static UserInfo fromMasked(UserModel model) {
        return new UserInfo(
                model.getLoginId(),
                maskName(model.getName()),
                model.getBirthDate(),
                model.getEmail()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return name.substring(0, name.length() - 1) + "*";
    }
}
