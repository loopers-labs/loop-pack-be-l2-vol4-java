package com.loopers.interfaces.api.user.dto;

import com.loopers.application.user.SignUpCommand;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record SignUpRequest(
    String loginId,
    String password,
    String name,
    String birthDate,
    String email,
    String gender
) {
    public SignUpCommand toCommand() {
        if (gender == null || gender.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "성별은 비어있을 수 없습니다.");
        }
        return new SignUpCommand(loginId, password, name, birthDate, email);
    }
}
