package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.Getter;

@Getter
@Entity
@Table(
    name = "product_metrics",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_product_metrics_date_product",
            columnNames = {"metric_date", "product_id"}),
    indexes = @Index(name = "idx_product_metrics_date", columnList = "metric_date"))
public class ProductMetricModel extends BaseEntity {

  @Column(name = "metric_date", nullable = false)
  private LocalDate metricDate;

  @Column(name = "product_id", nullable = false)
  private Long productId;

  @Column(name = "view_count", nullable = false)
  private Long viewCount;

  @Column(name = "like_count", nullable = false)
  private Long likeCount;

  @Column(name = "order_count", nullable = false)
  private Long orderCount;

  @Column(name = "order_quantity", nullable = false)
  private Long orderQuantity;

  @Column(name = "order_amount", nullable = false)
  private Long orderAmount;

  protected ProductMetricModel() {}
}
