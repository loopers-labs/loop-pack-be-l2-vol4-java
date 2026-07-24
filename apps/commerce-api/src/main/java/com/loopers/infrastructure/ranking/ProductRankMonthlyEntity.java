package com.loopers.infrastructure.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * mv_product_rank_monthly — 월간 TOP 100 랭킹 MV(week10).
 *
 * <p>{@code period_start} 는 그 달 1일, {@code period_end} 는 말일이다.
 * 스키마·설계 근거는 {@link ProductRankMvEntity} 참고.
 */
@Entity
@Table(name = "mv_product_rank_monthly")
public class ProductRankMonthlyEntity extends ProductRankMvEntity {

    protected ProductRankMonthlyEntity() {}
}
