package com.loopers.application.user;

import com.loopers.domain.user.User;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birth,
    String email
) {
    public static UserInfo from(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId().getValue(),
            user.getName().getValue(),
            user.getBirth().getValue(),
            user.getEmail().getValue()
        );
    }

    public static UserInfo fromMasked(User user) {
        return new UserInfo(
            user.getId(),
            user.getLoginId().getValue(),
            user.maskedName(),
            user.getBirth().getValue(),
            user.getEmail().getValue()
        );
    }
}
