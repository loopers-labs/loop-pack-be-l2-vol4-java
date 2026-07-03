package com.loopers.metrics.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 메트릭. product_id 를 PK 로 하는 단일 행이며, 증가 연산은 원자적 upsert 로 수행한다.
 * (엔티티는 조회·DDL 정의용. 갱신은 ProductMetricJpaRepository 의 native upsert)
 */
@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetric {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
