package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record Email(String value) {
    private static final String PATTERN = "^[^@]+@[^@]+\\.[^@]+$";

    public Email {
        if (value == null || !value.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }
    }

}
