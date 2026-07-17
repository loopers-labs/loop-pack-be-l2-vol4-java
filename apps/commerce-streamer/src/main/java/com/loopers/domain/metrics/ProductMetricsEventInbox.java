package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Getter
@Entity
@Table(
    name = "product_metrics_event_inbox",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_product_metrics_event_inbox_event_id",
            columnNames = "event_id"))
public class ProductMetricsEventInbox extends BaseEntity {

  @Column(name = "event_id", nullable = false, length = 36)
  private String eventId;

  protected ProductMetricsEventInbox() {}
}
