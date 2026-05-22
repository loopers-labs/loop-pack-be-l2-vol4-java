package com.loopers.vo;

import com.loopers.exception.ApiException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class Birthdate {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final String value;

    public Birthdate(String raw) {
        if (raw == null || raw.isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "생년월일을 입력해주세요.");
        String cleaned = raw.replace("-", "");
        if (!cleaned.matches("[0-9]{8}")) throw new ApiException(HttpStatus.BAD_REQUEST, "생년월일은 YYYYMMDD 또는 YYYY-MM-DD 형식이어야 합니다.");
        try { LocalDate.parse(cleaned, FMT); } catch (DateTimeParseException e) { throw new ApiException(HttpStatus.BAD_REQUEST, "존재하지 않는 날짜입니다."); }
        this.value = cleaned;
    }

    public String getValue() { return value; }
}
