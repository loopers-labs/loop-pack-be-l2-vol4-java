package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricModel;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductMetricJpaRepository extends JpaRepository<ProductMetricModel, Long> {

  Optional<ProductMetricModel> findByMetricDateAndProductId(LocalDate metricDate, Long productId);

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          "INSERT INTO product_metrics (metric_date, product_id, view_count, like_count,"
              + " order_count, order_quantity, order_amount, created_at, updated_at)"
              + " VALUES (:metricDate, :productId, :viewCount, :likeCount, :orderCount,"
              + " :orderQuantity, :orderAmount, NOW(6), NOW(6))"
              + " ON DUPLICATE KEY UPDATE"
              + " view_count = view_count + VALUES(view_count),"
              + " like_count = like_count + VALUES(like_count),"
              + " order_count = order_count + VALUES(order_count),"
              + " order_quantity = order_quantity + VALUES(order_quantity),"
              + " order_amount = order_amount + VALUES(order_amount),"
              + " updated_at = NOW(6)",
      nativeQuery = true)
  int increment(
      @Param("metricDate") LocalDate metricDate,
      @Param("productId") Long productId,
      @Param("viewCount") long viewCount,
      @Param("likeCount") long likeCount,
      @Param("orderCount") long orderCount,
      @Param("orderQuantity") long orderQuantity,
      @Param("orderAmount") long orderAmount);
}
