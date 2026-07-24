package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 주간·월간 랭킹 MV의 공통 컬럼(읽기 전용). 배치(commerce-batch)가 적재하고 API는 조회만 한다.
 * 소유는 배치지만 모듈 경계상 API가 자체 읽기용으로 동일 테이블을 매핑한다.
 */
@Getter
@MappedSuperclass
public abstract class MvProductRank extends BaseEntity {

    @Column(name = "period_key", nullable = false)
    private String periodKey;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    /** 주문 금액(order_amount) 합. 원천(product_metrics_hourly)에 수량이 없어 금액을 판매 신호로 쓴다. */
    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected MvProductRank() {
    }
}
