package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** 월간 TOP 100 랭킹 MV. */
@Entity
@Table(name = "mv_product_rank_monthly")
public class MonthlyProductRankModel extends ProductRankModel {

    protected MonthlyProductRankModel() {}

    public MonthlyProductRankModel(LocalDate periodStart, LocalDate periodEnd, Long productId,
                                   int ranking, double score, long likeCount, long salesCount,
                                   ZonedDateTime aggregatedAt) {
        super(periodStart, periodEnd, productId, ranking, score, likeCount, salesCount, aggregatedAt);
    }
}
