package com.loopers.vo;

import com.loopers.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class LoginId {
    private final String value;

    public LoginId(String raw) {
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "로그인 아이디를 입력해주세요.");
        this.value = raw;
    }

    public String getValue() { return value; }
}
