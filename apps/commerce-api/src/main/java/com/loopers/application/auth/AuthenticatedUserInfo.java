package com.loopers.application.auth;

import com.loopers.domain.user.UserModel;

public record AuthenticatedUserInfo(
    String loginId
) {
    public static AuthenticatedUserInfo from(UserModel user) {
        return new AuthenticatedUserInfo(user.getLoginId());
    }
}
