package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserSignUpInfo(Long userId, String loginId) {

    public static UserSignUpInfo from(UserModel user) {
        return new UserSignUpInfo(user.getId(), user.getLoginId().value());
    }
}
