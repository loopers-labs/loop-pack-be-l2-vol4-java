package com.loopers.interfaces.api.user.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordV1Request(
    @NotBlank String currentPassword,
    @NotBlank String newPassword
) {
}
