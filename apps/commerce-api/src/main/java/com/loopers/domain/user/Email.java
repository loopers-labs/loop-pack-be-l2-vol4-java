package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.regex.Pattern;

@Embeddable
public record Email(
    @Column(name = "email", nullable = false, length = 254)
    String value
) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");

    public static Email from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.");
        }

        if (value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("이메일은 %d자 이내여야 합니다.", MAX_LENGTH));
        }

        if (!ALLOWED_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.");
        }

        return new Email(value);
    }
}
