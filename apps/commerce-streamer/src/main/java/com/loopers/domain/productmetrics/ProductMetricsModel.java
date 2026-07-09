package com.loopers.domain.productmetrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 상품별 집계 지표. catalog-events(좋아요/조회) / order-events(판매량) Consumer가 upsert한다.
 */
@Getter
@Entity
@Table(name = "product_metrics")
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected ProductMetricsModel() {}

    public ProductMetricsModel(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        this.productId = productId;
        this.likeCount = 0;
        this.salesCount = 0;
        this.viewCount = 0;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        this.likeCount = Math.max(0, this.likeCount - 1);
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementSalesCount(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("판매 수량은 1 이상이어야 합니다.");
        }
        this.salesCount += quantity;
    }
}
