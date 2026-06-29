package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품 집계 읽기모델(파생). product_id 를 자연키 PK 로 갖는다.
 * 쓰기는 Consumer 가 native upsert(INSERT ... ON DUPLICATE KEY UPDATE)로 원자적으로 반영하므로,
 * 이 엔티티는 주로 조회용이다(updated_at 은 upsert 쿼리가 NOW() 로 갱신).
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getSalesCount() {
        return salesCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
