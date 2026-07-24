package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 상품 일간 지표 — (product_id, metric_date) 단위 집계 로우.
 * 일자는 이벤트 발생 시각(occurredAt)의 KST 날짜로 양자화한다(Redis 일간 랭킹과 동일 기준).
 *
 * <p>지표 성격이 두 갈래인 점에 주의한다.
 * <ul>
 *   <li>{@code salesCount}/{@code viewCount}/{@code likeDelta} — 그날의 <b>증분</b>. 기간 집계는 SUM.</li>
 *   <li>{@code likeCount} — 그날 마지막 시점의 <b>스냅샷</b>. 기간 집계에서 SUM 하면 같은 좋아요를
 *       일수만큼 중복 계산하므로 쓰지 않는다(주간/월간 점수는 likeDelta 를 쓴다).</li>
 * </ul>
 * commerce-batch 가 이 테이블을 읽어 주간/월간 MV 를 만든다(크로스-앱 계약).
 */
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_metrics_product_date", columnNames = {"product_id", "metric_date"}),
    indexes = @Index(name = "idx_product_metrics_date", columnList = "metric_date")
)
public class ProductMetrics extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;
    @Column(name = "like_count", nullable = false)
    private long likeCount;
    @Column(name = "like_delta", nullable = false)
    private long likeDelta;
    @Column(name = "sales_count", nullable = false)
    private long salesCount;
    @Column(name = "view_count", nullable = false)
    private long viewCount;
    @Column(name = "like_version", nullable = false)
    private long likeVersion;

    protected ProductMetrics() {}

    public Long getProductId() { return productId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getLikeCount() { return likeCount; }
    public long getLikeDelta() { return likeDelta; }
    public long getSalesCount() { return salesCount; }
    public long getViewCount() { return viewCount; }
    public long getLikeVersion() { return likeVersion; }
}
