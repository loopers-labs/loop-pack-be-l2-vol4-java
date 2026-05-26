package com.loopers.application.user;

import com.loopers.domain.user.User;

public record UserInfo(String loginId, String name, String birthDate, String email) {
    public static UserInfo from(User model) {
        return new UserInfo(
            model.getLoginId(),
            model.getName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
