package com.loopers.ranking.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.Arrays;

/**
 * 랭킹 조회 기간. 일간은 Redis, 주간/월간은 MV(DB)에서 읽는다.
 */
public enum RankPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }
        return Arrays.stream(values())
            .filter(p -> p.name().equalsIgnoreCase(value))
            .findFirst()
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST,
                "지원하지 않는 period입니다: " + value + " (daily|weekly|monthly)"));
    }
}
