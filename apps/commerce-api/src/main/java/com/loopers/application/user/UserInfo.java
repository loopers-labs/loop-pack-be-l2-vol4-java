package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    String loginId,
    String maskedName,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getLoginId(),
            user.maskedName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }
}
