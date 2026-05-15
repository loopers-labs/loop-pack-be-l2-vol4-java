package com.loopers.domain.value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthVO(
    LocalDate date
) {

    @Override
    public String toString() {
        return this.date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
