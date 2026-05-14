package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.ChangePasswordCommand;

public record ChangePasswordRequest(
    String currentPassword,
    String newPassword
) {
    public ChangePasswordCommand toCommand() {
        return new ChangePasswordCommand(currentPassword, newPassword);
    }
}
