package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 지표. Kafka 파이프라인으로 소비한 이벤트를 upsert로 누적한다.
 * PK는 자연키(product_id)이며, 갱신은 원자적 native upsert로 수행한다.
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetricModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricModel() {}

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getSalesCount() {
        return salesCount;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
