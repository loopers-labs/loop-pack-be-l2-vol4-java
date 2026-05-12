package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(String loginId, String name, String birthDate, String email) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getLoginId(),
            model.getName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
