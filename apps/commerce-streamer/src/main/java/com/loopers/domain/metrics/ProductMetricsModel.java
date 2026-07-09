package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 프로젝션(읽기 모델). 좋아요/판매/조회 수를 이벤트로 누적한다.
 * 자연키(product_id)를 PK 로 두어 upsert 의미가 자연스럽고 BaseEntity(대리키)를 상속하지 않는다.
 * version/updated_at 은 적용 순서 추적·감사용(교환법칙 델타라 정합성은 event_handled 로 확보).
 */
@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel {

    @Id
    @Column(name = "product_id")
    private Long productId;

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

    private ProductMetricsModel(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.salesCount = 0L;
        this.viewCount = 0L;
        this.version = 0L;
        this.updatedAt = ZonedDateTime.now();
    }

    public static ProductMetricsModel of(Long productId) {
        return new ProductMetricsModel(productId);
    }

    public void addLike(long delta) {
        this.likeCount = Math.max(0L, this.likeCount + delta);
        touch();
    }

    public void addView() {
        this.viewCount += 1L;
        touch();
    }

    public void addSales(long quantity) {
        this.salesCount += quantity;
        touch();
    }

    private void touch() {
        this.version += 1L;
        this.updatedAt = ZonedDateTime.now();
    }
}