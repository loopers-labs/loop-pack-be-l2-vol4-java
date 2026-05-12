package com.loopers.interfaces.api.user.dto;

import java.time.LocalDate;

public record SignUpV1Request(
    String loginId,
    String password,
    String name,
    LocalDate birthDate,
    String email
) {
}
