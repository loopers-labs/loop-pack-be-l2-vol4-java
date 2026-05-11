package com.loopers.domain.value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthVO(
    LocalDate date
) {
    public int toInt() {
        return Integer.parseInt(this.date.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
