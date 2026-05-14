package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record EncodedPassword(String value) {

    public EncodedPassword {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "암호화된 비밀번호는 비어있을 수 없습니다.");
        }
    }
}
