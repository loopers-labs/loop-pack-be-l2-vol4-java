package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record LoginId(String value) {
    private static final String PATTERN = "^[a-zA-Z0-9]{1,10}$";

    public LoginId {
        if (value == null || !value.matches(PATTERN)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID 는 영문/숫자 1~10자여야 합니다.");
        }
    }

}
