package com.loopers.infrastructure.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

final class RankingRedisKeys {

    private static final String KEY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingRedisKeys() {
    }

    static String dailyKey(LocalDate date) {
        return KEY_PREFIX + date.format(DATE_FORMAT);
    }
}
