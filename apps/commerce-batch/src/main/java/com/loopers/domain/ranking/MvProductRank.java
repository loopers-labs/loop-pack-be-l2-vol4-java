package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

/**
 * 주간·월간 랭킹 MV의 공통 컬럼. 조회 전용 사전 집계 결과로, 배치가 주기마다 삭제 후 재적재한다.
 * period_key(예: 2026-W29)로 한 주기의 TOP 100을 묶고, rank_no로 순위를 고정한다.
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

    protected MvProductRank(String periodKey, int rankNo, RankAggregate aggregate) {
        this.periodKey = periodKey;
        this.rankNo = rankNo;
        this.productId = aggregate.productId();
        this.score = aggregate.score();
        this.likeCount = aggregate.likeSum();
        this.orderAmount = aggregate.orderAmountSum();
        this.viewCount = aggregate.viewSum();
    }
}
