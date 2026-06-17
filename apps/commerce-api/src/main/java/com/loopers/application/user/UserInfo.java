package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(Long id, String loginId, String email, String nickname) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(user.getId(), user.getLoginId(), user.getEmail(), user.getNickname());
    }
}
