package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public final class UserPasswordPolicy {

    private static final Pattern PASSWORD_PATTERN =
        Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/\\\\|`~]{8,16}$");

    private UserPasswordPolicy() {}

    public static void validate(String rawPassword, LocalDate birthDate) {
        if (rawPassword == null || !PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.");
        }
        if (birthDate != null
            && (rawPassword.contains(birthDate.format(DateTimeFormatter.BASIC_ISO_DATE))
                || rawPassword.contains(birthDate.format(DateTimeFormatter.ISO_LOCAL_DATE)))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}