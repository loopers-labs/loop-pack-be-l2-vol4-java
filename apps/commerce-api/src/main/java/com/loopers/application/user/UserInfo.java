package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(Long id, String loginId, String name, String birthDate, String email) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
