package com.loopers.ranking.domain;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Locale;

public enum RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static RankingPeriod from(String value) {
        if (value == null || value.isBlank()) {
            return DAILY;
        }

        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new CoreException(
                    ErrorType.BAD_REQUEST, "period는 daily, weekly, monthly 중 하나여야 합니다.");
        }
    }
}
