package com.loopers.domain.ranking;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class ProductRankBatchRun {

    private final Long id;
    private final RankingPeriod period;
    private final LocalDate rankStartDate;
    private final LocalDate rankEndDate;
    private BatchRunStatus status;

    private ProductRankBatchRun(
        Long id,
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        BatchRunStatus status
    ) {
        this.id = id;
        this.period = period;
        this.rankStartDate = rankStartDate;
        this.rankEndDate = rankEndDate;
        this.status = status;
    }

    public static ProductRankBatchRun create(RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
        return new ProductRankBatchRun(null, period, rankStartDate, rankEndDate, BatchRunStatus.RUNNING);
    }

    public static ProductRankBatchRun restore(Long id, RankingPeriod period, LocalDate rankStartDate, LocalDate rankEndDate) {
        return restore(id, period, rankStartDate, rankEndDate, BatchRunStatus.RUNNING);
    }

    public static ProductRankBatchRun restore(
        Long id,
        RankingPeriod period,
        LocalDate rankStartDate,
        LocalDate rankEndDate,
        BatchRunStatus status
    ) {
        return new ProductRankBatchRun(id, period, rankStartDate, rankEndDate, status);
    }

    public void markCompleted() {
        this.status = BatchRunStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = BatchRunStatus.FAILED;
    }
}
