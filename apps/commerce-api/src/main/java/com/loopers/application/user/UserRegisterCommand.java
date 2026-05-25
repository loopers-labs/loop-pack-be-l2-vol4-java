package com.loopers.application.user;

import com.loopers.domain.user.UserModel;

import java.time.LocalDate;

public record UserRegisterCommand(
    String loginId,
    String password,
    String name,
    LocalDate birthDate,
    String email
) {
    public UserModel toDomain() {
        return new UserModel(loginId, password, name, birthDate, email);
    }
}
