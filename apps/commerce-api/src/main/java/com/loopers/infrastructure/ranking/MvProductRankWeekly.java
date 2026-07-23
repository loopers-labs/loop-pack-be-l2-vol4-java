package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 주간 랭킹 Materialized View 의 조회용 매핑(읽기 전용). 배치(commerce-batch)가 적재하며,
 * API 는 (year_week, rank_no) 순으로 읽기만 한다. rank 는 MySQL 예약어라 컬럼명은 rank_no.
 */
@Entity
@Table(name = "mv_product_rank_weekly")
@IdClass(MvProductRankWeeklyId.class)
public class MvProductRankWeekly {

    @Id
    @Column(name = "year_week")
    private String yearWeek;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    protected MvProductRankWeekly() {}

    public String getYearWeek() {
        return yearWeek;
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
