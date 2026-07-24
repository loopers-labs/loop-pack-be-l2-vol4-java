package com.loopers.ranking.application;

import java.util.List;

public record DailyRankingEntries(
    List<Long> productIds,
    long totalElements
) {

    public DailyRankingEntries {
        productIds = List.copyOf(productIds);
    }
}
