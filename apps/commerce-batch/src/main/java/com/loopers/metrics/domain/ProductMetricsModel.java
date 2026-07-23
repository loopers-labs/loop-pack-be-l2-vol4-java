package com.loopers.metrics.domain;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * product_metrics 읽기 모델 (batch 전용 사본).
 * 배치는 이 테이블의 집계 카운트를 읽어 랭킹 점수를 계산한다. 쓰기는 streamer가 담당한다.
 * (앱이 분리돼 있어 각자 필요한 컬럼만 매핑해 보유한다)
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", unique = true, nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected ProductMetricsModel() {}

    public ProductMetricsModel(Long productId, long likeCount, long salesCount, long viewCount) {
        this.productId = productId;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
    }

    public Long getProductId() { return productId; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public long getViewCount() { return viewCount; }
}
