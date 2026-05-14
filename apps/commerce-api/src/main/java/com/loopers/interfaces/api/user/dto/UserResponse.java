package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.UserInfo;

public record UserResponse(
    Long id,
    String loginId,
    String name,
    String birthDate,
    String email
) {
    public static UserResponse empty() {
        return null;
    }

    public static UserResponse from(UserInfo userInfo) {
        return new UserResponse(
            userInfo.id(),
            userInfo.loginId(),
            userInfo.name(),
            userInfo.birthDate(),
            userInfo.email()
        );
    }

    public static UserResponse maskedFrom(UserInfo userInfo) {
        return new UserResponse(
            userInfo.id(),
            userInfo.loginId(),
            mask(userInfo.name()),
            userInfo.birthDate(),
            userInfo.email()
        );
    }

    private static String mask(String name) {
        return name.substring(0, name.length() - 1) + "*";
    }
}
