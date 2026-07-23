package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 배치가 롤업의 '원천'으로 읽는 일자별 상품 집계(읽기모델). streamer 가 실시간으로 적재한다.
 * 배치 앱은 이 테이블을 native 집계 SQL 로 직접 읽지만, 앱별 자체 엔티티 관례에 따라 여기에도 매핑을 둔다
 * — ddl-auto 로 테이블을 만들고 테스트 데이터를 준비하기 위함이다.
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

    private DailyProductMetrics(Long productId, LocalDate metricDate, long likeCount, long salesCount, long viewCount) {
        this.productId = productId;
        this.metricDate = metricDate;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.viewCount = viewCount;
        this.updatedAt = ZonedDateTime.now(ZoneId.of("UTC"));
    }

    public static DailyProductMetrics of(Long productId, LocalDate metricDate, long likeCount, long salesCount, long viewCount) {
        return new DailyProductMetrics(productId, metricDate, likeCount, salesCount, viewCount);
    }

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
}
