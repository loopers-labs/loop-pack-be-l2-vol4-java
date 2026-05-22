package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserInfo(
        String loginId,
        String name,
        LocalDate birthDate,
        String email
) {
    public static UserInfo from(UserModel userModel) {
        return new UserInfo(
                userModel.getLoginId(),
                userModel.getName(),
                userModel.getBirthDate(),
                userModel.getEmail()
        );
    }
}
