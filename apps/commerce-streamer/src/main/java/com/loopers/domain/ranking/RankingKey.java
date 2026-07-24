package com.loopers.domain.ranking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class RankingKey {

    private static final String PREFIX = "ranking:all:";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private RankingKey() {
    }

    /** 주어진 날짜의 일간 랭킹 키. 예: ranking:all:20260713 */
    public static String daily(LocalDate date) {
        return PREFIX + date.format(FMT);
    }

    /** 어떤 시점(Instant)이든 KST 기준 '그날'의 일간 랭킹 키로 변환한다. */
    public static String dailyOf(Instant instant) {
        return daily(instant.atZone(KST).toLocalDate());
    }
}
