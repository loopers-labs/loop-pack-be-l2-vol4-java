package com.loopers.interfaces.api.user.dto;

public record ChangePasswordV1Request(
    String currentPassword,
    String newPassword
) {
}
