package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

public enum RankingMvPeriod {
    WEEKLY,
    MONTHLY;

    public static RankingMvPeriod from(String value) {
        try {
            return RankingMvPeriod.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "period는 WEEKLY 또는 MONTHLY여야 합니다: " + value);
        }
    }
}
