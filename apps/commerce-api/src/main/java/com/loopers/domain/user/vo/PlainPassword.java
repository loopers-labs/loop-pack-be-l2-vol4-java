package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public record PlainPassword(String value) {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final String ALLOWED_CHARACTERS = "A-Za-z0-9!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/\\\\|`~";
    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[%s]{%d,%d}$".formatted(ALLOWED_CHARACTERS, MIN_LENGTH, MAX_LENGTH));

    public PlainPassword {
        if (value == null || !PASSWORD_PATTERN.matcher(value).matches()) {
            throw new CoreException(
                ErrorType.BAD_REQUEST,
                "비밀번호는 %d~%d자의 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.".formatted(MIN_LENGTH, MAX_LENGTH)
            );
        }
    }

    public static PlainPassword of(String value) {
        return new PlainPassword(value);
    }

    public static PlainPassword of(String value, BirthDate birthDate) {
        PlainPassword password = of(value);
        password.validateNotContains(birthDate);
        return password;
    }

    private void validateNotContains(BirthDate birthDate) {
        if (value.contains(birthDate.value().format(DateTimeFormatter.BASIC_ISO_DATE))
            || value.contains(birthDate.value().format(DateTimeFormatter.ISO_LOCAL_DATE))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
