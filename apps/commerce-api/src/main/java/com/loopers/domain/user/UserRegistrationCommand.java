package com.loopers.domain.user;

public record UserRegistrationCommand(
    String loginId,
    String password,
    String name,
    String birthDate,
    String email
) {}
