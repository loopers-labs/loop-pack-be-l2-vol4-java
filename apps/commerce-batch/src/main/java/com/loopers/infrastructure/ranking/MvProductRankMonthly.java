package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 월간 랭킹 Materialized View — 한 달(year_month)의 TOP 100 을 미리 계산해 rank 까지 매겨 저장한 조회 전용 테이블.
 * 주간 MV 와 동일 구조이며 버킷 식별자만 yyyy-MM 이다. rank 는 MySQL 예약어라 컬럼명은 rank_no 로 둔다.
 */
@Entity
@Table(name = "mv_product_rank_monthly")
@IdClass(MvProductRankMonthlyId.class)
public class MvProductRankMonthly {

    @Id
    @Column(name = "`year_month`")
    private String yearMonth;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected MvProductRankMonthly() {}

    public String getYearMonth() {
        return yearMonth;
    }

    public Long getProductId() {
        return productId;
    }

    public double getScore() {
        return score;
    }

    public int getRankNo() {
        return rankNo;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
