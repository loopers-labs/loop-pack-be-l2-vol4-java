package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.format.DateTimeFormatter;

public record UserInfo(String loginId, String maskedName, String birthDate, String email) {

    private static final DateTimeFormatter BIRTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getLoginId(),
            model.getMaskedName(),
            model.getBirthDate().format(BIRTH_DATE_FORMATTER),
            model.getEmail()
        );
    }
}
