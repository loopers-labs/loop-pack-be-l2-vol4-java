package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 월간 랭킹 MV — period_key 는 'yyyy-MM' (예: 2026-07). */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mv_rank_monthly_key_product", columnNames = {"period_key", "product_id"}),
    indexes = @Index(name = "idx_mv_rank_monthly_key_rank", columnList = "period_key, rank_no")
)
public class MvProductRankMonthly extends MvProductRank {

    protected MvProductRankMonthly() {}

    public MvProductRankMonthly(String periodKey, ProductRankScore row) {
        super(periodKey, row);
    }
}
