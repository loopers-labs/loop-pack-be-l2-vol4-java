package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class RankingKeyGenerator {

    private static final String PREFIX = "ranking:all:";

    private RankingKeyGenerator() {}

    public static String dailyKey(LocalDate date) {
        return PREFIX + date.format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
