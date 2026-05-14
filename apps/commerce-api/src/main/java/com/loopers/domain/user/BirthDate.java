package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class BirthDate {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT);

    private final LocalDate value;

    public static BirthDate of(String value) {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }

        try {
            return new BirthDate(LocalDate.parse(value, FORMATTER));
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다.");
        }
    }
}
