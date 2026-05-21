package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.ChangePasswordCommand;

public record ChangePasswordRequest(String newPassword) {
    public ChangePasswordCommand toCommand(String currentPassword) {
        return new ChangePasswordCommand(currentPassword, newPassword);
    }
}
