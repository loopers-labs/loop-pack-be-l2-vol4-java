package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

public record UserInfo(String loginId, String name, String birthDate, String email) {

    public static UserInfo from(UserModel model) {
        String maskedName = maskLastChar(model.getName());
        return new UserInfo(model.getLoginId(), maskedName, model.getBirthDate(), model.getEmail());
    }

    private static String maskLastChar(String name) {
        return name.substring(0, name.length() - 1) + "*";
    }
}
