package com.loopers.infrastructure.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 상품×일자별 지표(주문/좋아요/조회수). product_metrics(평생 누적)와 같은 트랜잭션에서
 * ProductDailyMetricsJpaRepository의 upsert 쿼리로 함께 적재되며, 주간/월간 랭킹 배치의
 * 입력으로만 쓰인다(조회는 commerce-batch 쪽에서 기간 합산 쿼리로 수행).
 */
@Getter
@Entity(name = "ProductDailyMetrics")
@Table(
    name = "product_daily_metrics",
    indexes = @Index(name = "idx_daily_metrics_product", columnList = "product_id, metric_date")
)
@IdClass(ProductDailyMetricsEntity.DailyMetricsId.class)
public class ProductDailyMetricsEntity {

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "order_count", nullable = false)
    private Long orderCount = 0L;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductDailyMetricsEntity() {}

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        this.updatedAt = ZonedDateTime.now();
    }

    public static class DailyMetricsId implements Serializable {
        private LocalDate metricDate;
        private Long productId;

        public DailyMetricsId() {}
    }
}
