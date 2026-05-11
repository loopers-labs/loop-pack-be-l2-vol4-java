package com.loopers.domain.value;

import java.util.regex.Pattern;

public record EmailVO(
    String email
) {
    private static final Pattern PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public EmailVO {
        if (!PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바르지 않는 이메일 형식 입니다.");
        }
    }
}
