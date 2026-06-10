package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;

public record UserInfo(
    Long id,
    String loginId,
    String name,
    String maskedName,
    String birthDate,
    String email,
    Gender gender
) {
    public static UserInfo from(UserModel model) {
        return new UserInfo(
            model.getId(),
            model.getLoginId(),
            model.getName(),
            model.getMaskedName(),
            model.getBirthDate(),
            model.getEmail(),
            model.getGender()
        );
    }
}
