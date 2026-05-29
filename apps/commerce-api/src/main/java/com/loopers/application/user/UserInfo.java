package com.loopers.application.user;

import com.loopers.domain.user.User;

public record UserInfo(
    Long id,
    String loginId
) {
    public static UserInfo from(User user) {
        return new UserInfo(user.getId(), user.getLoginId());
    }
}
