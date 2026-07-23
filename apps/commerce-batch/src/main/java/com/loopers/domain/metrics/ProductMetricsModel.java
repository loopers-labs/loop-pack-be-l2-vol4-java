package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * commerce-streamer가 적재하는 일별 상품 집계(product_metrics)의 배치측 매핑. 배치는 이 테이블을 날짜 범위로 읽어 주간/월간 랭킹을 만든다.
 * commerce-streamer 스키마와 동일하게 복합 자연키(product_id, metric_date)를 둔다. 배치는 읽기 전용이며 of(...)는 테스트 seed 용이다.
 */
@Entity
@Table(name = "product_metrics")
@IdClass(ProductMetricsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    private ProductMetricsModel(Long productId, LocalDate metricDate, long viewCount, long likeCount, long salesCount) {
        this.productId = productId;
        this.metricDate = metricDate;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.version = 0L;
        this.updatedAt = ZonedDateTime.now();
    }

    public static ProductMetricsModel of(Long productId, LocalDate metricDate, long viewCount, long likeCount, long salesCount) {
        return new ProductMetricsModel(productId, metricDate, viewCount, likeCount, salesCount);
    }
}