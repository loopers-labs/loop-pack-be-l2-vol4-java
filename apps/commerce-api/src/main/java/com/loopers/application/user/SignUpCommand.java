package com.loopers.application.user;

import com.loopers.domain.user.Gender;
import com.loopers.domain.user.User;

public record SignUpCommand(
    String loginId,
    String password,
    String name,
    String birthDate,
    String email,
    Gender gender
) {
    public User toModel() {
        return new User(loginId, password, name, birthDate, email, gender);
    }
}
