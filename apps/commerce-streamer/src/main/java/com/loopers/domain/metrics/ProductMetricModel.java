package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 상품별·일자별 집계 지표. Kafka 파이프라인으로 소비한 이벤트를 upsert로 누적한다.
 * PK는 (stat_date, product_id) 복합 자연키이며, 갱신은 원자적 native upsert로 수행한다.
 * 일자 축이 있으므로 특정 기간(주/월)의 행을 뽑아 집계할 수 있다.
 */
@Entity
@Table(name = "product_metrics")
@IdClass(ProductMetricId.class)
public class ProductMetricModel {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

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

    public LocalDate getStatDate() {
        return statDate;
    }

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
