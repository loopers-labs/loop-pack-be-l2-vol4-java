package com.loopers.infrastructure.catalog.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = {
        @Index(name = "idx_mv_product_rank_monthly_period_rank", columnList = "period_start_date, period_end_date, `rank`")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_mv_product_rank_monthly_period_rank",
            columnNames = {"period_start_date", "period_end_date", "`rank`"}
        ),
        @UniqueConstraint(
            name = "uk_mv_product_rank_monthly_period_product",
            columnNames = {"period_start_date", "period_end_date", "product_id"}
        )
    }
)
public class MonthlyProductRankMvJpaEntity extends ProductRankMvJpaEntity {
}
