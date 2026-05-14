package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    String userId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel user) {
        return new UserInfo(
            user.getUserId(),
            user.getMaskedName(),
            user.getBirthDate(),
            user.getEmail()
        );
    }

}
