package com.loopers.interfaces.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;

public record SignUpV1Request(
    @NotBlank String loginId,
    @NotBlank String password,
    @NotBlank String name,
    @NotNull @Past LocalDate birthDate,
    @NotBlank @Email String email
) {
}
