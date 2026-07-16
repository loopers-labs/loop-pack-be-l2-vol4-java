package com.loopers.infrastructure.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

final class RankingRedisKeys {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String HOURLY_KEY_PREFIX = "ranking:hourly:";
    private static final DateTimeFormatter HOURLY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    static final Duration TTL = Duration.ofDays(2);
    static final Duration HOURLY_TTL = Duration.ofHours(2);
    static final String WEIGHTS_KEY = "ranking:weights";

    private RankingRedisKeys() {
    }

    static String dailyKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMAT);
    }

    static String hourlyKey(LocalDateTime dateTime) {
        return HOURLY_KEY_PREFIX + dateTime.format(HOURLY_DATE_FORMAT);
    }
}
