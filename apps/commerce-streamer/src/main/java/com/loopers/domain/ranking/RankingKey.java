package com.loopers.domain.ranking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 ZSET 키 전략 — 일간 키 {@code ranking:all:{yyyyMMdd}}, TTL 2일.
 * 오늘 키는 날짜가 바뀐 뒤에도 전일 랭킹 조회에 쓰여야 하므로 TTL을 1일이 아닌 2일로 잡는다.
 */
public final class RankingKey {

    public static final Duration DAILY_TTL = Duration.ofDays(2);

    private static final String DAILY_PREFIX = "ranking:all:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private RankingKey() {}

    public static String daily(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("랭킹 키 날짜는 필수입니다.");
        }
        return DAILY_PREFIX + date.format(DATE_FORMAT);
    }
}
