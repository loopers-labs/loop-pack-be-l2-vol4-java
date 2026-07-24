package com.loopers.ranking.application;

import java.time.LocalDate;

public interface ProductRankingSourceQuery {

    long countProductsWithMetrics(
        LocalDate periodStartInclusive,
        LocalDate aggregationEndDateInclusive
    );
}
