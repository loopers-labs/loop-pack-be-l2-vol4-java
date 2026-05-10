package com.loopers.domain.value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthVO(
        LocalDate localDate
) {
    public int toInt() {
        return Integer.parseInt(this.localDate.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
