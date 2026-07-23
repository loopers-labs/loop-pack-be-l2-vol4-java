package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 랭킹 ZSET 키 전략 — commerce-streamer(적재 측)의 RankingKey와 동일한 계약을 갖는다.
 * 두 앱이 공유 모듈 없이 각자 정의하고 있어 키 형식 변경 시 양쪽을 함께 수정해야 한다 (payload 계약과 동일한 방식).
 */
public final class RankingKey {

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
