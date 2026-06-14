package com.loopers.order.domain;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record OrderSearchPeriod(
    ZonedDateTime startDateTime,
    ZonedDateTime endExclusiveDateTime
) {

    public static OrderSearchPeriod of(LocalDate startAt, LocalDate endAt) {
        return new OrderSearchPeriod(
            startOfDay(startAt),
            nextDayStart(endAt)
        );
    }

    private static ZonedDateTime startOfDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneId.systemDefault());
    }

    private static ZonedDateTime nextDayStart(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay(ZoneId.systemDefault());
    }
}
