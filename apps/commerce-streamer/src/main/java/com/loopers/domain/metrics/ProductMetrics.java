package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 메트릭 — commerce-api 이벤트를 소비해 만드는 read 모델.
 *
 * <p>productId 가 자연 키(PK)다. 갱신은 JPA dirty checking 이 아니라
 * 네이티브 upsert(<code>INSERT ... ON DUPLICATE KEY UPDATE</code>)의 원자 증감으로 수행한다 —
 * 서로 다른 토픽(좋아요/판매/조회)의 리스너 스레드가 같은 상품을 동시에 갱신해도 Lost Update 가 없다.
 * 이 엔티티는 스키마 정의(ddl-auto)와 조회용이다.
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sale_count", nullable = false)
    private long saleCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    /** 마지막 이벤트 반영 시각 — 집계 최신성 판단 기준. */
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getSaleCount() {
        return saleCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
