package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;

import java.time.format.DateTimeFormatter;

public record UserInfo(Long id, String loginId, String name, String email, String birthDate, Gender gender) {

    public String maskedName() {
        return name.substring(0, name.length() - 1) + "*";
    }

    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getEmail(),
            model.getBirthDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
            model.getGender()
        );
    }
}
