package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record Name(String value) {

    private static final int MIN_LENGTH = 2;
    private static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("[가-힣]+");

    public static Name from(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("이름은 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }

        if (!ALLOWED_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 한글만 허용됩니다.");
        }

        return new Name(value);
    }
}
