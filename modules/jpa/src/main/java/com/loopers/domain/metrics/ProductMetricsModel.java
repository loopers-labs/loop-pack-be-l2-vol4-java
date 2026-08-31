package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "product_metrics",
    uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "date", nullable = false, updatable = false)
    private LocalDate date;

    @Column(name = "like_count", nullable = false)
    private long likeCount = 0;

    @Column(name = "view_count", nullable = false)
    private long viewCount = 0;

    @Column(name = "sales_count", nullable = false)
    private long salesCount = 0;

    @Column(name = "last_event_at")
    private ZonedDateTime lastEventAt;

    public ProductMetricsModel(UUID productId, LocalDate date) {
        this.productId = productId;
        this.date = date;
    }

    /**
     * Kafka 파티션 순서 보장은 되지만, 리밸런스/재처리 등으로 뒤늦게 온 오래된 이벤트를 걸러내는 안전장치.
     * like/unlike처럼 서로의 역연산이라 순서가 결과에 영향을 주는 연산에만 적용한다 — view/sales처럼
     * 단순 누적(교환법칙 성립)이라 순서와 무관한 연산에는 적용하지 않는다(적용하면 정상 이벤트가 부당하게 유실됨).
     */
    public boolean isStale(ZonedDateTime eventTime) {
        return lastEventAt != null && !eventTime.isAfter(lastEventAt);
    }

    public void incrementLike(ZonedDateTime eventTime) {
        this.likeCount++;
        this.lastEventAt = eventTime;
    }

    public void decrementLike(ZonedDateTime eventTime) {
        this.likeCount = Math.max(0, this.likeCount - 1);
        this.lastEventAt = eventTime;
    }

    public void incrementView() {
        this.viewCount++;
    }

    public void incrementSales() {
        this.salesCount++;
    }
}
