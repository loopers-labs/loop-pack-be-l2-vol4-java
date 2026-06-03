package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class PasswordVO {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    private static final Pattern PASSWORD_PATTERN =
            Pattern.compile(
                    "^[a-zA-Z0-9!@#$%^&*()_+\\-={}\\[\\]:;\"'<>,.?/\\\\|`~]+$"
            );

    private static final DateTimeFormatter FULL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final DateTimeFormatter YEARLESS_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyMMdd");

    private static final DateTimeFormatter MONTH_DAY_FORMAT =
            DateTimeFormatter.ofPattern("MMdd");

    private final String encodedValue;

    private PasswordVO(String encodedValue) {
        this.encodedValue = encodedValue;
    }

    public static PasswordVO fromEncoded(String encodedValue) {
        validateNotBlank(encodedValue);
        return new PasswordVO(encodedValue);
    }

    public static void validatePolicy(
            String rawPassword,
            LocalDate birthDate
    ) {
        validateNotBlank(rawPassword);
        validateLength(rawPassword);
        validateCharacter(rawPassword);
        validateBirthDate(rawPassword, birthDate);
    }

    private static void validateNotBlank(String password) {
        if (password == null || password.isBlank()) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 비어있을 수 없습니다."
            );
        }
    }

    private static void validateLength(
            String password
    ) {
        if (
                password.length() < MIN_LENGTH
                        || password.length() > MAX_LENGTH
        ) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 8자 이상 16자 이하여야 합니다."
            );
        }
    }

    private static void validateCharacter(
            String password
    ) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다."
            );
        }
    }

    private static void validateBirthDate(
            String password,
            LocalDate birthDate
    ) {
        String fullDate =
                birthDate.format(FULL_DATE_FORMAT);

        String yearlessDate =
                birthDate.format(YEARLESS_DATE_FORMAT);

        String monthDay =
                birthDate.format(MONTH_DAY_FORMAT);

        if (
                password.contains(fullDate)
                        || password.contains(yearlessDate)
                        || password.contains(monthDay)
        ) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호에 생년월일을 포함할 수 없습니다."
            );
        }
    }

    public String value() {
        return encodedValue;
    }
}
