package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.vo.UserName;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId().value(),
            model.getName().value(),
            model.getBirthDate().value(),
            model.getEmail().value()
        );
    }

    public String maskedName() {
        return UserName.of(name).mask();
    }
}
