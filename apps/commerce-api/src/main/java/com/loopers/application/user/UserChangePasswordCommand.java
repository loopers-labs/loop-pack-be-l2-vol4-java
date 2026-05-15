package com.loopers.application.user;

public record UserChangePasswordCommand(String currentPassword, String newPassword) {}
