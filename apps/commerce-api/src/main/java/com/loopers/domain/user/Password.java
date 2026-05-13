package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

public class Password {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;

    /**
     * 영문 대소문자 / 숫자 / 일반적인 특수문자만 허용
     * 허용 특수문자: ! @ # $ % ^ & * ( ) _ + - = [ ] { } | \ ; : ' " , . < > / ?
     */
    private static final Pattern ALLOWED_CHARS =
            Pattern.compile("^[A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{}|\\\\;:'\",.<>/?]+$");

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter MMDD = DateTimeFormatter.ofPattern("MMdd");

    private final String value;

    public Password(String value, LocalDate birthday) {
        validate(value, birthday);
        this.value = value;
    }

    private void validate(String value, LocalDate birthday) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 null이거나 공백일 수 없습니다.");
        }
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST,
                    "비밀번호는 " + MIN_LENGTH + "자 이상 " + MAX_LENGTH + "자 이하여야 합니다."
            );
        }
        if (!ALLOWED_CHARS.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 허용되지 않은 문자가 포함되어 있습니다.");
        }
        if (birthday != null && containsBirthday(value, birthday)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    private boolean containsBirthday(String value, LocalDate birthday) {
        return value.contains(birthday.format(YYYYMMDD))
                || value.contains(birthday.format(YYMMDD))
                || value.contains(birthday.format(MMDD));
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Password)) return false;
        Password password = (Password) o;
        return Objects.equals(value, password.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
