package com.loopers.domain.value;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public record BirthVO(
        LocalDate birth
) {
    public int toInt() {
        return Integer.parseInt(this.birth.format(DateTimeFormatter.BASIC_ISO_DATE));
    }
}
