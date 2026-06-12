package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.UserName;

import java.time.LocalDate;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    String maskedName,
    LocalDate birthDate,
    String email
) {
    public static UserInfo from(User model) {
        UserName userName = model.getName();
        return new UserInfo(
            model.getId(),
            model.getLoginId().value(),
            userName.value(),
            userName.mask(),
            model.getBirthDate().value(),
            model.getEmail().value()
        );
    }
}