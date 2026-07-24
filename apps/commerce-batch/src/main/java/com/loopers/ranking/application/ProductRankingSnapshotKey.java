package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.Objects;

public record ProductRankingSnapshotKey(
    RankingPeriod period,
    LocalDate aggregationEndDate,
    int revision
) {

    public ProductRankingSnapshotKey {
        Objects.requireNonNull(period, "period must not be null");
        Objects.requireNonNull(aggregationEndDate, "aggregationEndDate must not be null");
        if (period == RankingPeriod.DAILY) {
            throw new IllegalArgumentException("period must be WEEKLY or MONTHLY");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision must be at least 1");
        }
    }

    public LocalDate periodStart() {
        return period.periodStart(aggregationEndDate);
    }
}
