package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public record RawPassword(String value) {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^[A-Za-z0-9\\p{Punct}]{8,16}$");

    public RawPassword {
        if (value == null || value.isBlank() || !PASSWORD_PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다.");
        }
    }
}
