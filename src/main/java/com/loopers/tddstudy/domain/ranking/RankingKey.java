package com.loopers.tddstudy.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;

public class RankingKey {

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingKey() {}

    public static String daily(LocalDate date) {
        return PREFIX + date.format(FORMAT);
    }
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static LocalDate dateOf(long occurredAt) {
        return Instant.ofEpochMilli(occurredAt).atZone(ZONE).toLocalDate();
    }

}
