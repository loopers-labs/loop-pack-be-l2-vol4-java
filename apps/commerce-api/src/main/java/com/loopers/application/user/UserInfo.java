package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String maskedName,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId(),
            model.getMaskedName(),
            model.getBirthDate(),
            model.getEmail()
        );
    }
}
