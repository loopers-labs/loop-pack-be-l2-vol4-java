package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품 집계 읽기모델(파생). product_id 를 자연키 PK 로 갖는다.
 * 쓰기는 Consumer 가 native upsert(INSERT ... ON DUPLICATE KEY UPDATE)로 원자적으로 반영하므로,
 * 이 엔티티는 주로 조회용이다(updated_at 은 upsert 쿼리가 NOW() 로 갱신).
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long likeCount;

    @Column(name = "sales_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long salesCount;

    @Column(name = "view_count", nullable = false, columnDefinition = "bigint not null default 0")
    private long viewCount;

    /**
     * 재고 수량의 최신 스냅샷(파생). like/sales/view 는 delta 누적이지만, 재고는 '절대 상태'라
     * 이벤트가 실어온 값으로 덮어쓴다. 순서역전·재전송으로 '오래된' 이벤트가 도착해도 최신 상태를
     * 되돌리지 않도록 stock_version(고수위 표시)으로 최신성을 가드한다.
     */
    @Column(name = "stock_quantity", nullable = false, columnDefinition = "bigint not null default 0")
    private long stockQuantity;

    @Column(name = "stock_version", nullable = false, columnDefinition = "bigint not null default 0")
    private long stockVersion;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getSalesCount() {
        return salesCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public long getStockQuantity() {
        return stockQuantity;
    }

    public long getStockVersion() {
        return stockVersion;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
