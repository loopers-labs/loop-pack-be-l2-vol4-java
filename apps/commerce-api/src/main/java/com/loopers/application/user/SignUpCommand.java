package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.UserModel;

public record SignUpCommand(
    String loginId,
    String password,
    String name,
    String birthDate,
    String email,
    Gender gender
) {
    public UserModel toModel() {
        return new UserModel(loginId, password, name, birthDate, email, gender);
    }
}
