package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birth,
    String email
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.getName(),
            user.getBirth(),
            user.getEmail()
        );
    }

    public static UserInfo fromMasked(UserModel user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId(),
            user.maskedName(),
            user.getBirth(),
            user.getEmail()
        );
    }
}
