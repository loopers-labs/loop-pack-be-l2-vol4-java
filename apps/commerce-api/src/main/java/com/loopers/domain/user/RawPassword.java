package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.regex.Pattern;

public final class RawPassword {

    private static final Pattern PATTERN = Pattern.compile("^[\\x21-\\x7E]{8,16}$");

    private final String value;

    public RawPassword(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자로 구성된 8~16자여야 합니다.");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }
}
