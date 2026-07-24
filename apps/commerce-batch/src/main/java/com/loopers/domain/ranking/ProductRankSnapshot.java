package com.loopers.domain.ranking;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ProductRankSnapshot {

    private final RankingPeriod period;
    private final LocalDate rankStartDate;
    private final LocalDate rankEndDate;
    private final Long batchRunId;
    private final Long productId;
    private final int rankNo;
    private final double score;
    private boolean active;

    private ProductRankSnapshot(
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Long batchRunId,
        Long productId,
        int rankNo,
        double score
    ) {
        this.period = period;
        this.rankStartDate = rankStartDate;
        this.rankEndDate = rankEndDate;
        this.batchRunId = batchRunId;
        this.productId = productId;
        this.rankNo = rankNo;
        this.score = score;
        this.active = false;
    }

    public static ProductRankSnapshot createInactive(
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        Long batchRunId,
        Long productId,
        int rankNo,
        double score
    ) {
        return new ProductRankSnapshot(period, rankStartDate, rankEndDate, batchRunId, productId, rankNo, score);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
