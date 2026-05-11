package com.loopers.application.user;

import java.time.LocalDate;

public record SignUpCommand(
    String loginId,
    String password,
    String name,
    LocalDate birthDate,
    String email
) {}