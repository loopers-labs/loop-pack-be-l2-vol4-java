package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(
    Long id,
    String loginId
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(user.getId(), user.getLoginId());
    }
}
