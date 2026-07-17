package com.loopers.ranking.domain;

import java.time.LocalDate;

/**
 * 랭킹 조회의 서빙 창 정책. 최근 2일(오늘·어제)만 ZSET 으로 서빙한다.
 * 창 밖(더 과거·미래)은 404 로 안내한다. 순수 로직(I/O 없음).
 */
public final class RankingDatePolicy {

    private static final int SERVING_DAYS = 2;

    private RankingDatePolicy() {
    }

    public static boolean isServable(LocalDate date, LocalDate today) {
        return !date.isAfter(today) && !date.isBefore(today.minusDays(SERVING_DAYS - 1));
    }
}
