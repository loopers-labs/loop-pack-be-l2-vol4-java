package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 월간 랭킹 Materialized View 의 조회용 매핑(읽기 전용). year_month 는 MySQL 예약어라 백틱으로 감싼다.
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
}
