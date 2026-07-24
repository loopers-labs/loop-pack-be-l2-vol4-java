package com.loopers.infrastructure.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * mv_product_rank_weekly — 주간 TOP 100 랭킹 MV(week10).
 *
 * <p>{@code period_start} 는 그 주 <b>월요일</b>(ISO-8601), {@code period_end} 는 일요일이다.
 * 스키마·설계 근거는 {@link ProductRankMvEntity} 참고.
 */
@Entity
@Table(name = "mv_product_rank_weekly")
public class ProductRankWeeklyEntity extends ProductRankMvEntity {

    protected ProductRankWeeklyEntity() {}
}
