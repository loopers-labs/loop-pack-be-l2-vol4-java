package com.loopers.metrics.domain;

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
 * 상품×일자 단위 메트릭. 주간·월간 랭킹이 기간을 SUM 해 뽑으므로 날짜가 PK 에 들어간다.
 * PK 컬럼 순서가 (stat_date, product_id) 가 되도록 statDate 를 먼저 선언한다 — 기간 범위 스캔이 선행이다.
 * (엔티티는 조회·DDL 정의용. 갱신은 ProductMetricJpaRepository 의 native upsert)
 */
@Entity
@Table(name = "product_metrics")
@IdClass(ProductMetricId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetric {

    @Id
    @Column(name = "stat_date")
    private LocalDate statDate;

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
