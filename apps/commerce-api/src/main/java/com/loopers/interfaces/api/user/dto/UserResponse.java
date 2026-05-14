package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.UserInfo;

public record UserResponse(
    Long id,
    String loginId,
    String name,
    String birthDate,
    String email
) {
    public static UserResponse from(UserInfo userInfo) {
        return new UserResponse(
            userInfo.id(),
            userInfo.loginId(),
            userInfo.name(),
            userInfo.birthDate(),
            userInfo.email()
        );
    }
}
