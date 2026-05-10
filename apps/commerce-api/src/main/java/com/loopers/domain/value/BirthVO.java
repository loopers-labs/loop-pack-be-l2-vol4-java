package com.loopers.domain.value;

import java.time.LocalDate;

public record BirthVO(
        LocalDate localDate
) {
    public int toInt() {
        return Integer.parseInt(this.localDate.toString());
    }
}
