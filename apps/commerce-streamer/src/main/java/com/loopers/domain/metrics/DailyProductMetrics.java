package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 상품의 '일자별' 집계 읽기모델(파생). (product_id, metric_date) 를 복합 PK 로 갖는다.
 * 누적 스냅샷인 product_metrics 와 달리 날짜 축이 있어, 배치가 이 테이블을 기간(주/월)으로 쪼개 롤업한다.
 * 쓰기는 Consumer 가 native upsert(INSERT ... ON DUPLICATE KEY UPDATE)로 원자적으로 반영한다.
 * 재고는 '절대 상태'라 일자별 합산이 무의미하므로 담지 않는다 — like/sales/view 만 누적한다.
 */
@Entity
@Table(name = "daily_product_metrics")
@IdClass(DailyProductMetricsId.class)
public class DailyProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Column(name = "like_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long likeCount;

    @Column(name = "sales_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long salesCount;

    @Column(name = "view_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long viewCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected DailyProductMetrics() {}

    public Long getProductId() {
        return productId;
    }

    public LocalDate getMetricDate() {
        return metricDate;
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
