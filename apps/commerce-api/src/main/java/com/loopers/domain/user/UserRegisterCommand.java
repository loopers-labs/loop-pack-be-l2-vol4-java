package com.loopers.domain.user;

import java.time.LocalDate;

public record UserRegisterCommand(
    String loginId,
    String password,
    String name,
    LocalDate birthDate,
    String email
) {}
