package com.loopers.ranking.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

import java.time.Instant;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "product_metrics",
    indexes = @Index(
        name = "idx_product_metrics_product_id_metric_date",
        columnList = "product_id, metric_date"
    )
)
public class ProductMetric {

    @EmbeddedId
    private ProductMetricId id;

    @Check(
        name = "ck_product_metrics_view_count_non_negative",
        constraints = "view_count >= 0"
    )
    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_delta", nullable = false)
    private long likeDelta;

    @Check(
        name = "ck_product_metrics_order_quantity_non_negative",
        constraints = "order_quantity >= 0"
    )
    @Column(name = "order_quantity", nullable = false)
    private long orderQuantity;

    @Check(
        name = "ck_product_metrics_order_amount_non_negative",
        constraints = "order_amount >= 0"
    )
    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;
}
