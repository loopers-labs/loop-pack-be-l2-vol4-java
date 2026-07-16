package com.loopers.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class DailyRankingKey {
    public static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private DailyRankingKey() {
    }

    public static String from(LocalDate date) {
        return "ranking:all:" + Objects.requireNonNull(date, "date").format(DATE_FORMATTER);
    }

    public static String from(ZonedDateTime occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        return from(occurredAt.withZoneSameInstant(ZONE_ID).toLocalDate());
    }

    public static String member(Long productId) {
        return Objects.requireNonNull(productId, "productId").toString();
    }
}
