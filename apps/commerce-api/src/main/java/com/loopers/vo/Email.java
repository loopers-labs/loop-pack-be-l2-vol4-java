package com.loopers.vo;

import com.loopers.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class Email {
    private final String value;

    public Email(String raw) {
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "이메일을 입력해주세요.");
        if (!raw.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) throw new ApiException(HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        this.value = raw;
    }

    public String getValue() { return value; }
}
