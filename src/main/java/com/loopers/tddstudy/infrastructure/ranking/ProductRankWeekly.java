package com.loopers.tddstudy.infrastructure.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "mv_product_rank_weekly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weekly_period_product",
                columnNames = {"period_key", "product_id"}
        )
)
public class ProductRankWeekly extends ProductRankMv {

    protected ProductRankWeekly() {}

    public ProductRankWeekly(String periodKey, int rankNo, Long productId, double score,
                             long likeCount, long salesCount, long viewCount) {
        super(periodKey, rankNo, productId, score, likeCount, salesCount, viewCount);
    }
}
