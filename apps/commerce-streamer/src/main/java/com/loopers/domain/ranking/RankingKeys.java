package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 일간 랭킹 키 계산 — 이벤트 발생 시각(occurredAt) 기준 Asia/Seoul 날짜로 양자화한다.
 * 키 형식은 commerce-api 의 com.loopers.domain.ranking.RankingKeys 와 크로스-앱 계약이다(동일하게 유지할 것).
 */
public final class RankingKeys {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private RankingKeys() {}

    public static String daily(LocalDate date) {
        return "ranking:all:" + DAY.format(date);
    }

    public static String dedup(String eventId) {
        return "ranking:dedup:" + eventId;
    }

    public static LocalDate dateOf(String occurredAt) {
        return ZonedDateTime.parse(occurredAt).withZoneSameInstant(ZONE).toLocalDate();
    }
}
