package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricDelta;
import com.loopers.domain.metrics.ProductMetricsProjectionRepository;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductMetricsEventProcessor {

  private static final int SUPPORTED_EVENT_VERSION = 1;
  private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

  private final ProductMetricsProjectionRepository projectionRepository;

  @Transactional
  public boolean process(ProductActivityEventMessage event) {
    validate(event);
    LocalDate metricDate = event.occurredAt().atZone(SEOUL_ZONE).toLocalDate();
    ProductMetricDelta delta = toDelta(event);

    if (!projectionRepository.markProcessed(event.eventId())) {
      return false;
    }
    projectionRepository.increment(metricDate, event.productId(), delta);
    return true;
  }

  private ProductMetricDelta toDelta(ProductActivityEventMessage event) {
    return switch (event.eventType()) {
      case PRODUCT_VIEWED -> ProductMetricDelta.viewed();
      case PRODUCT_LIKED -> ProductMetricDelta.liked();
      case PRODUCT_ORDERED ->
          ProductMetricDelta.ordered(
              event.quantity(), Math.multiplyExact(event.unitPrice(), (long) event.quantity()));
    };
  }

  private void validate(ProductActivityEventMessage event) {
    if (event == null) {
      throw new IllegalArgumentException("이벤트는 필수입니다.");
    }
    if (event.version() != SUPPORTED_EVENT_VERSION) {
      throw new IllegalArgumentException("지원하지 않는 이벤트 버전입니다: " + event.version());
    }
    if (event.eventId() == null) {
      throw new IllegalArgumentException("eventId는 필수입니다.");
    }
    if (event.eventType() == null) {
      throw new IllegalArgumentException("eventType은 필수입니다.");
    }
    if (event.occurredAt() == null) {
      throw new IllegalArgumentException("occurredAt은 필수입니다.");
    }
    if (event.productId() == null || event.productId() <= 0) {
      throw new IllegalArgumentException("productId는 양수여야 합니다.");
    }
    if (event.eventType() == ProductActivityEventType.PRODUCT_ORDERED) {
      validateOrder(event);
    }
  }

  private void validateOrder(ProductActivityEventMessage event) {
    if (event.unitPrice() == null || event.unitPrice() < 0) {
      throw new IllegalArgumentException("주문 이벤트의 unitPrice는 0 이상이어야 합니다.");
    }
    if (event.quantity() == null || event.quantity() <= 0) {
      throw new IllegalArgumentException("주문 이벤트의 quantity는 양수여야 합니다.");
    }
  }
}
