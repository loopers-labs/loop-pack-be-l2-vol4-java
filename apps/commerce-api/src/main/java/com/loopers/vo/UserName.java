package com.loopers.vo;

import com.loopers.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class UserName {
    private final String value;

    public UserName(String raw) {
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "이름을 입력해주세요.");
        if (raw.length() < 2) throw new ApiException(HttpStatus.BAD_REQUEST, "이름은 2자 이상이어야 합니다.");
        if (!raw.matches("[가-힣a-zA-Z]+")) throw new ApiException(HttpStatus.BAD_REQUEST, "이름은 한글 또는 영문만 입력 가능합니다.");
        this.value = raw;
    }

    public String getValue() { return value; }
}
