package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(
        Long id,
        String userId,
        String maskedName,
        String email,
        String birthDay
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
                user.getId(),
                user.getUserId().getValue(),
                maskName(user.getName().getValue()),
                user.getEmail().getValue(),
                user.getBirthDay().getValue()
        );
    }

    private static String maskName(String name) {
        return name.substring(0, name.length() - 1) + "*";
    }
}
