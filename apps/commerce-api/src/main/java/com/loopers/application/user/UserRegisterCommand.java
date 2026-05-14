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
    public UserModel toDomain(String encodedPassword) {
        return new UserModel(loginId, encodedPassword, name, birthDate, email);
    }
}
