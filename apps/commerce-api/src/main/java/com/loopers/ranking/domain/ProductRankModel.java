package com.loopers.ranking.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * 조회 전용 랭킹 MV의 공통 행(읽기 모델). batch가 적재하고 api는 읽기만 한다.
 * 주간/월간 테이블이 같은 구조라 공통 부모로 둔다.
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
        this.productId = productId;
        this.rank = rank;
        this.score = score;
    }

    public Long getProductId() { return productId; }
    public int getRank() { return rank; }
    public double getScore() { return score; }
}
