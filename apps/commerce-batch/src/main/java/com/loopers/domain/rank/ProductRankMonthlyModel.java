package com.loopers.domain.rank;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "mv_product_rank_monthly", uniqueConstraints = @UniqueConstraint(columnNames = {"period_key", "rank_no"}))
@NoArgsConstructor(access = PROTECTED)
public class ProductRankMonthlyModel extends ProductRankMv {

    private ProductRankMonthlyModel(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        super(periodKey, rankNo, productId, score, aggregatedAt);
    }

    public static ProductRankMonthlyModel of(String periodKey, int rankNo, Long productId, double score, ZonedDateTime aggregatedAt) {
        return new ProductRankMonthlyModel(periodKey, rankNo, productId, score, aggregatedAt);
    }
}