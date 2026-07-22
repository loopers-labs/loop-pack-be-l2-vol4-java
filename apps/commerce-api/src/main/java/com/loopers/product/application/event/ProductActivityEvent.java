package com.loopers.product.application.event;

import java.time.Instant;
import java.util.UUID;

public record ProductActivityEvent(
    UUID eventId,
    int version,
    ProductActivityEventType eventType,
    Instant occurredAt,
    Long productId,
    Long unitPrice,
    Integer quantity) {
  public static final int CURRENT_VERSION = 1;
  public static final String TOPIC = "commerce.product-activity.v1";

  public static ProductActivityEvent viewed(Long productId) {
    return new ProductActivityEvent(
        UUID.randomUUID(),
        CURRENT_VERSION,
        ProductActivityEventType.PRODUCT_VIEWED,
        Instant.now(),
        productId,
        null,
        null);
  }

  public static ProductActivityEvent liked(Long productId) {
    return new ProductActivityEvent(
        UUID.randomUUID(),
        CURRENT_VERSION,
        ProductActivityEventType.PRODUCT_LIKED,
        Instant.now(),
        productId,
        null,
        null);
  }
}
