package com.loopers.application.user;

import java.time.LocalDate;

import com.loopers.domain.user.UserModel;

public record UserMyInfo(String loginId, String name, LocalDate birthDate, String email) {

    public static UserMyInfo from(UserModel user) {
        return new UserMyInfo(
            user.getLoginId().value(),
            user.getName().maskedValue(),
            user.getBirthDate().value(),
            user.getEmail().value()
        );
    }
}
