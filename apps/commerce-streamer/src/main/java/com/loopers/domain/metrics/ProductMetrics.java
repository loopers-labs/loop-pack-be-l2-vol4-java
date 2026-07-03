package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품별 집계 뷰 — like_count / view_count / sales_count 를 자체 관리한다.
 * <p>
 * commerce-streamer 가 카프카 이벤트를 소비해 UPSERT 로 갱신한다. 원본 도메인(product.like_count) 과 분리된
 * <b>읽기 최적화 사본</b> — eventual consistency 를 허용한다.
 * <p>
 * updatedAt 이벤트 시각 비교로 오래된 이벤트를 무시한다.
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetrics extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "last_event_at", nullable = false)
    private ZonedDateTime lastEventAt;

    protected ProductMetrics() {}

    public ProductMetrics(Long productId, ZonedDateTime firstEventAt) {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId 는 양수여야 합니다.");
        }
        if (firstEventAt == null) {
            throw new IllegalArgumentException("firstEventAt 은 필수입니다.");
        }
        this.productId = productId;
        this.likeCount = 0;
        this.viewCount = 0;
        this.salesCount = 0;
        this.lastEventAt = firstEventAt;
    }

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public long getSalesCount() {
        return salesCount;
    }

    public ZonedDateTime getLastEventAt() {
        return lastEventAt;
    }

    /**
     * 이벤트 시각이 마지막 반영 시각보다 오래됐으면 무시. 그 외에는 delta 를 반영하고 시각을 갱신.
     * <b>주의:</b> like/view/sales 카운트는 서로 독립이므로, 오래된 이벤트라도 다른 카운트에는 영향을 주지 않는다.
     * 여기서는 이벤트 유형별 개별 시각 관리를 하지 않고 단일 lastEventAt 만 관리 — 학습 단계 단순화.
     */
    public void applyLikeDelta(long delta, ZonedDateTime at) {
        if (!isNewer(at)) return;
        this.likeCount = Math.max(0, this.likeCount + delta);
        this.lastEventAt = at;
    }

    public void incrementView(ZonedDateTime at) {
        if (!isNewer(at)) return;
        this.viewCount += 1;
        this.lastEventAt = at;
    }

    public void addSales(long quantity, ZonedDateTime at) {
        if (quantity <= 0) return;
        if (!isNewer(at)) return;
        this.salesCount += quantity;
        this.lastEventAt = at;
    }

    private boolean isNewer(ZonedDateTime at) {
        return at != null && !at.isBefore(this.lastEventAt);
    }
}
