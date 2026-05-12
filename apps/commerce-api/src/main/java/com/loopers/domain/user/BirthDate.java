package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthDate(LocalDate value) {

    private static final DateTimeFormatter COMPACT_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter SHORT_COMPACT_FORMATTER = DateTimeFormatter.ofPattern("yyMMdd");

    public static BirthDate from(LocalDate value) {
        if (value == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.");
        }

        if (value.isAfter(LocalDate.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 미래일 수 없습니다.");
        }

        return new BirthDate(value);
    }

    public String toCompact() {
        return value.format(COMPACT_FORMATTER);
    }

    public String toShortCompact() {
        return value.format(SHORT_COMPACT_FORMATTER);
    }
}
