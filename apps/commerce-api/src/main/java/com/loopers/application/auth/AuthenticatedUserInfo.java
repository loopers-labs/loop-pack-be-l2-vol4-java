package com.loopers.application.auth;

import com.loopers.domain.user.User;

public record AuthenticatedUserInfo(
    String loginId
) {
    public static AuthenticatedUserInfo from(User user) {
        return new AuthenticatedUserInfo(user.getLoginId());
    }
}
