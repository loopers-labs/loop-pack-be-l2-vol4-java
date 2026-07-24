package com.loopers.infrastructure.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

// 실제 Reader는 JdbcCursorItemReader로 직접 조회한다 — 이 엔티티는 test 프로필 ddl-auto=create가
// 스키마를 생성하고 DatabaseCleanUp이 테이블을 인식하게 하기 위한 읽기전용 스키마 정의 목적.
@Entity
@Table(name = "product_metric_daily")
@Getter
public class ProductMetricDailyJpaEntity {

    @EmbeddedId
    private ProductMetricDailyId id;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_delta_count", nullable = false)
    private long likeDeltaCount;

    @Column(name = "purchase_quantity", nullable = false)
    private long purchaseQuantity;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricDailyJpaEntity() {}
}
