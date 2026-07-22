package com.loopers.batch.job.catalog.ranking;

import java.time.LocalDate;

public record ProductRankItem(
    LocalDate periodStartDate,
    LocalDate periodEndDate,
    Long rank,
    Long productId,
    Double score
) {
}
