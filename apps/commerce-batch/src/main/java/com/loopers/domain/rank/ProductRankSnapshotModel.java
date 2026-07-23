package com.loopers.domain.rank;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 주간·월간 랭킹 MV 공통 스냅샷 — (순위, 상품, 점수, 집계 기간)을 담는 조회 전용 행.
 * `rank`는 MySQL 8 예약어라 컬럼명을 `rank_no`로 둔다.
 */
@Getter
@MappedSuperclass
public abstract class ProductRankSnapshotModel extends BaseEntity {

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    protected ProductRankSnapshotModel() {}

    protected ProductRankSnapshotModel(int rankNo, Long productId, double score, LocalDate periodStart, LocalDate periodEnd) {
        if (rankNo < 1) {
            throw new IllegalArgumentException("순위는 1 이상이어야 합니다.");
        }
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("집계 기간은 필수입니다.");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("집계 종료일은 시작일 이후여야 합니다.");
        }
        this.rankNo = rankNo;
        this.productId = productId;
        this.score = score;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }
}
