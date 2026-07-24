package com.loopers.infrastructure.ranking.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * product_daily_metrics 매핑(commerce-batch 읽기 전용). commerce-streamer가 적재한 상품×일자별
 * 지표를 이 배치에서는 period(주/월) 범위로 SUM해서 랭킹 점수 계산에만 사용한다.
 */
@Getter
@Entity(name = "ProductDailyMetricsForRanking")
@Table(name = "product_daily_metrics")
@IdClass(ProductDailyMetricsEntity.DailyMetricsId.class)
public class ProductDailyMetricsEntity {

    @Id
    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "order_count")
    private Long orderCount;

    @Column(name = "like_count")
    private Long likeCount;

    @Column(name = "view_count")
    private Long viewCount;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected ProductDailyMetricsEntity() {}

    public static class DailyMetricsId implements Serializable {
        private LocalDate metricDate;
        private Long productId;

        public DailyMetricsId() {}
    }
}
