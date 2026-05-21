package com.loopers.interfaces.auth;

import com.loopers.domain.user.UserModel;

public record LoginUser(Long id, String loginId) {

    public static LoginUser from(UserModel user) {
        return new LoginUser(user.getId(), user.getLoginId().getValue());
    }
}
