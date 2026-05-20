package com.loopers.domain.value;

import jakarta.persistence.Embeddable;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Embeddable
public record BirthVO(
    LocalDate date
) {

    public BirthVO {
        Objects.requireNonNull(date, "생년월일은 null일 수 없습니다.");
    }

    @Override
    public String toString() {
        return this.date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
