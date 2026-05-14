package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(
        Long id,
        String userid,
        String maskedName,
        String email,
        String birthDay
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
                user.getId(),
                user.getUserid(),
                maskName(user.getName()),
                user.getEmail(),
                user.getBirthDay()
        );
    }

    private static String maskName(String name) {
        if (name.length() == 1) return "*";
        return name.substring(0, name.length() - 1) + "*";
    }
}
