package com.loopers.domain.user.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class PlainPassword {

    private static final DateTimeFormatter FORMATTER_8 = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMATTER_6 = DateTimeFormatter.ofPattern("yyMMdd");
    private static final DateTimeFormatter FORMATTER_4 = DateTimeFormatter.ofPattern("MMdd");

    private final String value;

    public PlainPassword(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.");
        }
        if (value.length() < 8 || value.length() > 16) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8자 이상 16자 이하여야 합니다.");
        }
        if (!value.matches("^[\\x21-\\x7E]+$")) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용 가능합니다.");
        }
        this.value = value;
    }

    public PlainPassword(String value, LocalDate birthDate) {
        this(value);
        if (value.contains(birthDate.format(FORMATTER_8))
                || value.contains(birthDate.format(FORMATTER_6))
                || value.contains(birthDate.format(FORMATTER_4))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public String value() {
        return value;
    }
}
