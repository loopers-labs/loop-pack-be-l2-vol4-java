package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 주간 랭킹 Materialized View — 한 주(year_week)의 TOP 100 을 미리 계산해 rank 까지 매겨 저장한 조회 전용 테이블.
 * 배치가 DELETE→INSERT 로 그 주 전체를 원자적으로 재적재하므로(D7), 조회는 (year_week, rank_no) 순으로 곧장 읽는다.
 * rank 는 MySQL 예약어라 컬럼명은 rank_no 로 둔다.
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

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

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

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
