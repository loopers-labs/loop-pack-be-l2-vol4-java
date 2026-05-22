package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record UserName(String value) {

    public UserName {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.");
        }
    }

    public static UserName of(String value) {
        return new UserName(value);
    }

    public String mask() {
        return value.substring(0, value.length() - 1) + "*";
    }
}
