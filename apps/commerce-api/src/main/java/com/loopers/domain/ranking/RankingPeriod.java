package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

/**
 * 랭킹 집계 기간. 저장소가 갈린다 — DAILY 는 Redis ZSET(실시간), WEEKLY/MONTHLY 는 배치가 적재한 MV(조회 전용).
 */
public enum RankingPeriod {
    DAILY, WEEKLY, MONTHLY;

    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        try {
            return RankingPeriod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "지원하지 않는 랭킹 기간입니다: " + value);
        }
    }
}
