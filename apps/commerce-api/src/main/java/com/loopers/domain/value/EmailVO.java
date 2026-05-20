package com.loopers.domain.value;

import jakarta.persistence.Embeddable;

import java.util.Objects;
import java.util.regex.Pattern;

@Embeddable
public record EmailVO(
    String email
) {
    private static final Pattern PATTERN =
            Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public EmailVO {
        Objects.requireNonNull(email, "이메일은 null일 수 없습니다.");
        if (email.isBlank()) {
            throw new IllegalArgumentException("이메일을 입력해주세요.");
        }
        if (!PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("올바르지 않는 이메일 형식 입니다.");
        }
    }
}
