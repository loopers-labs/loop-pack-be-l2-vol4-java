package com.loopers.domain.productmetrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * 상품별 시간 단위 원천 집계(SOT)의 배치 측 매핑. 소유는 streamer지만, 모듈 경계상 batch가
 * 자체 읽기용으로 동일 테이블(product_metrics_hourly)을 매핑한다. batch는 이 표를 기간 범위로
 * 합산해 주간·월간 랭킹 MV를 만든다.
 * <p>
 * 컬럼 정의는 streamer의 원본과 동일해야 한다 — 한쪽만 바뀌면 조회가 조용히 어긋난다.
 */
@Getter
@Entity
@Table(
        name = "product_metrics_hourly",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_product_metrics_hourly", columnNames = {"product_id", "bucket_hour", "source"}),
        indexes = @Index(name = "idx_product_metrics_hourly_bucket", columnList = "bucket_hour")
)
public class ProductMetricsHourly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** 집계 구간의 시작 정시 (Asia/Seoul 기준으로 절삭된 시각). */
    @Column(name = "bucket_hour", nullable = false)
    private ZonedDateTime bucketHour;

    /** 유입 경로. 조회에만 의미가 있고, 좋아요·주문은 UNKNOWN으로 모인다. */
    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    /** 좋아요 순증감의 합. 취소가 많으면 음수가 될 수 있다. */
    @Column(name = "like_delta", nullable = false)
    private long likeDelta;

    /** 주문 금액(단가×수량)의 합. */
    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    protected ProductMetricsHourly() {
    }

    private ProductMetricsHourly(Long productId, ZonedDateTime bucketHour, String source,
                                long viewCount, long likeDelta, long orderAmount) {
        this.productId = Objects.requireNonNull(productId, "productId는 필수다.");
        this.bucketHour = Objects.requireNonNull(bucketHour, "bucketHour는 필수다.");
        this.source = Objects.requireNonNull(source, "source는 필수다.");
        this.viewCount = viewCount;
        this.likeDelta = likeDelta;
        this.orderAmount = orderAmount;
    }

    public static ProductMetricsHourly of(Long productId, ZonedDateTime bucketHour, String source,
                                          long viewCount, long likeDelta, long orderAmount) {
        return new ProductMetricsHourly(productId, bucketHour, source, viewCount, likeDelta, orderAmount);
    }
}
