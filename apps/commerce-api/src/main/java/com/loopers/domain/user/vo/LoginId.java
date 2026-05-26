package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Embeddable;

@Embeddable
public record LoginId(String value) {

    public LoginId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 비어있을 수 없습니다.");
        }
        if (!value.matches("^[a-zA-Z0-9]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아이디는 영문과 숫자만 사용 가능합니다.");
        }
    }
}
