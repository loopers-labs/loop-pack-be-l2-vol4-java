package com.loopers.member.interfaces.api;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
