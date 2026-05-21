package com.loopers.application.user;

public record ChangePasswordCommand(
    String currentPassword,
    String newPassword
) {}
