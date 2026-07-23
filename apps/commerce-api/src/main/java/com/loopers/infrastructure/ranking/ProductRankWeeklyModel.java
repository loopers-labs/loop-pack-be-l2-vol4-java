package com.loopers.infrastructure.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(
        name = "mv_product_rank_weekly", 
        uniqueConstraints = @UniqueConstraint(columnNames = {"period_key", "rank_no"})
)
@NoArgsConstructor(access = PROTECTED)
public class ProductRankWeeklyModel extends ProductRankMvView {

    private ProductRankWeeklyModel(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        super(periodKey, rankNo, productId, score, aggregatedAt);
    }

    public static ProductRankWeeklyModel of(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        return new ProductRankWeeklyModel(periodKey, rankNo, productId, score, aggregatedAt);
    }
}