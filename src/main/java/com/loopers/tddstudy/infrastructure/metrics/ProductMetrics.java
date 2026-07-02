package com.loopers.tddstudy.infrastructure.metrics;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    private Long productId;
    private long likeCount;
    private long salesCount;
    private long viewCount;
    private LocalDateTime updatedAt;
    private long lastEventAt;   // 마지막으로 반영한 이벤트의 occurredAt (epoch millis)

    protected ProductMetrics() {}

    public ProductMetrics(Long productId) {
        this.productId = productId;
        this.updatedAt = LocalDateTime.now();
    }

    public void addLike(long d)  { this.likeCount = Math.max(0, likeCount + d); touch(); }
    public void addSales(long d) { this.salesCount += d; touch(); }
    public void addView(long d)  { this.viewCount += d; touch(); }
    private void touch() { this.updatedAt = LocalDateTime.now(); }

    public Long getProductId() { return productId; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public long getViewCount() { return viewCount; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void markEvent(long occurredAt) { this.lastEventAt = occurredAt; }
    public long getLastEventAt() { return lastEventAt; }
}
