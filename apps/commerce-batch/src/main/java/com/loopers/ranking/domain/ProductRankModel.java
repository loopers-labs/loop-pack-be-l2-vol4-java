package com.loopers.ranking.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * 조회 전용 랭킹 MV의 공통 행. 배치가 product_metrics를 집계해 점수순 TOP N을 적재한다.
 * 주간/월간 테이블이 컬럼 구조가 같아 공통 부모로 둔다 (매핑 테이블만 하위에서 지정).
 */
@MappedSuperclass
public abstract class ProductRankModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // MySQL 8 예약어(RANK)라 컬럼명은 rank_no로 매핑한다
    @Column(name = "rank_no", nullable = false)
    private int rank;

    @Column(name = "score", nullable = false)
    private double score;

    protected ProductRankModel() {}

    protected ProductRankModel(Long productId, int rank, double score) {
        if (productId == null) {
            throw new IllegalArgumentException("productId는 null일 수 없습니다.");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank는 1 이상이어야 합니다. rank=" + rank);
        }
        this.productId = productId;
        this.rank = rank;
        this.score = score;
    }

    public Long getProductId() { return productId; }
    public int getRank() { return rank; }
    public double getScore() { return score; }
}
