package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDate;

// 상품·날짜(metric_date) 단위의 일별 이벤트 집계다. 누적 총합인 ProductMetricsModel과 달리
// (product_id, metric_date)마다 1행을 두어 주간/월간 배치가 기간으로 잘라 합산할 수 있게 한다.
// 세 지표 모두 델타(누적) 모델이라 원자적 UPSERT(INSERT ... ON DUPLICATE KEY UPDATE)만으로 동시성이 해결된다.
// 값 변경은 ProductMetricsDailyJpaRepository의 native UPSERT로만 이뤄진다 - 엔티티엔 증감 메서드를 두지 않는다.
@Getter
@Entity
@Table(
    name = "product_metrics_daily",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_product_metrics_daily_product_id_metric_date",
        columnNames = {"product_id", "metric_date"}
    )
)
public class ProductMetricsDailyModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    // KST 기준 집계 대상 날짜. 애플리케이션에서 LocalDate.now()로 계산해 주입한다(DB 세션 타임존에 의존하지 않음).
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "order_count", nullable = false)
    private Long orderCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    protected ProductMetricsDailyModel() {
    }

    public ProductMetricsDailyModel(Long productId, LocalDate metricDate) {
        this.productId = productId;
        this.metricDate = metricDate;
        this.likeCount = 0L;
        this.orderCount = 0L;
        this.viewCount = 0L;
    }
}
