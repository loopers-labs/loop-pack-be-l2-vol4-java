package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 주간 랭킹 MV — period_key 는 ISO 주 기준 'yyyy-Www' (예: 2026-W30). */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_mv_rank_weekly_key_product", columnNames = {"period_key", "product_id"}),
    indexes = @Index(name = "idx_mv_rank_weekly_key_rank", columnList = "period_key, rank_no")
)
public class MvProductRankWeekly extends MvProductRank {

    protected MvProductRankWeekly() {}

    public MvProductRankWeekly(String periodKey, ProductRankScore row) {
        super(periodKey, row);
    }
}
