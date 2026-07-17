package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsEventInbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricsEventInboxJpaRepository
    extends JpaRepository<ProductMetricsEventInbox, Long> {

  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "INSERT IGNORE INTO product_metrics_event_inbox"
              + " (event_id, created_at, updated_at) VALUES (:eventId, NOW(6), NOW(6))",
      nativeQuery = true)
  int insertIgnore(@Param("eventId") String eventId);

  boolean existsByEventId(String eventId);
}
