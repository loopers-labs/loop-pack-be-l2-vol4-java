package com.loopers.domain.productmetrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 상품별 "일자별" 집계 지표 (일간 롤업) — 주간·월간 랭킹 배치의 집계 원천.
 * 기존 {@link ProductMetricsModel}(상품별 전체 누적 1행)과 달리 (product_id, metric_date) 단위로 행이 존재해,
 * 특정 기간(지난 7일/30일)의 지표를 분리해 집계할 수 있다. catalog/order Consumer가 이벤트 처리일 기준으로 upsert 한다.
 */
@Getter
@Entity
@Table(
    name = "product_metrics_daily",
    uniqueConstraints = @UniqueConstraint(name = "uk_pmd_product_date", columnNames = {"product_id", "metric_date"}),
    indexes = @Index(name = "idx_pmd_metric_date", columnList = "metric_date")
)
public class ProductMetricsDailyModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "metric_date", nullable = false, updatable = false)
    private LocalDate metricDate;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected ProductMetricsDailyModel() {}

    public ProductMetricsDailyModel(Long productId, LocalDate metricDate) {
        if (productId == null) {
            throw new IllegalArgumentException("상품 ID는 필수입니다.");
        }
        if (metricDate == null) {
            throw new IllegalArgumentException("집계 일자는 필수입니다.");
        }
        this.productId = productId;
        this.metricDate = metricDate;
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
