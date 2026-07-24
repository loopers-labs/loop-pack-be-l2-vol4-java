package com.loopers.domain.ranking;

import java.time.LocalDate;

public record RankingDateRange(
    RankingPeriod period,
    LocalDate startDate,
    LocalDate endDate
) {
}
