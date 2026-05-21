package com.loopers.interfaces.auth;

import com.loopers.domain.user.UserModel;

public record AuthenticatedUser(
    String loginId
) {
    public static AuthenticatedUser from(UserModel user) {
        return new AuthenticatedUser(user.getLoginId());
    }
}
