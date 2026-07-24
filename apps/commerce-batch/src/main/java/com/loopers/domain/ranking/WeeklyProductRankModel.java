package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** 주간 TOP 100 랭킹 MV. */
@Entity
@Table(name = "mv_product_rank_weekly")
public class WeeklyProductRankModel extends ProductRankModel {

    protected WeeklyProductRankModel() {}

    public WeeklyProductRankModel(LocalDate periodStart, LocalDate periodEnd, Long productId,
                                  int ranking, double score, long likeCount, long salesCount,
                                  ZonedDateTime aggregatedAt) {
        super(periodStart, periodEnd, productId, ranking, score, likeCount, salesCount, aggregatedAt);
    }
}
