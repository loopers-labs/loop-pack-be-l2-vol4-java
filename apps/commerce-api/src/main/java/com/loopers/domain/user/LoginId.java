package com.loopers.domain.user;

import java.util.regex.Pattern;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public record LoginId(String value) {

    private static final int MIN_LENGTH = 4;
    private static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9]+");

    public static LoginId of(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }

        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, String.format("로그인 ID는 %d~%d자만 허용됩니다.", MIN_LENGTH, MAX_LENGTH));
        }

        if (!ALLOWED_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문 및 숫자만 허용됩니다.");
        }

        return new LoginId(value);
    }
}
