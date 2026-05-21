package com.loopers.application.user;

public record SignUpCommand(
    String loginId,
    String password,
    String name,
    String birthDate,
    String email
) {}
