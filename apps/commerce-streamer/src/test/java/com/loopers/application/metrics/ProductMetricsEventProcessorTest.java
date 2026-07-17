package com.loopers.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.domain.metrics.ProductMetricDelta;
import com.loopers.domain.metrics.ProductMetricsProjectionRepository;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductMetricsEventProcessorTest {

  private final ProductMetricsProjectionRepository projectionRepository =
      mock(ProductMetricsProjectionRepository.class);
  private final ProductMetricsEventProcessor processor =
      new ProductMetricsEventProcessor(projectionRepository);

  @DisplayName("조회 이벤트를 서울 기준 일자의 조회 수 1로 집계한다.")
  @Test
  void projectsViewedEventUsingSeoulDate() {
    // arrange
    ProductActivityEventMessage event =
        event(
            UUID.randomUUID(),
            ProductActivityEventType.PRODUCT_VIEWED,
            Instant.parse("2026-07-15T15:00:00Z"),
            null,
            null);
    when(projectionRepository.markProcessed(event.eventId())).thenReturn(true);

    // act
    boolean processed = processor.process(event);

    // assert
    assertThat(processed).isTrue();
    verify(projectionRepository)
        .increment(LocalDate.of(2026, 7, 16), event.productId(), ProductMetricDelta.viewed());
  }

  @DisplayName("주문 이벤트는 주문 수 1과 상품 수량 및 주문 금액을 집계한다.")
  @Test
  void projectsOrderedEventWithQuantityAndAmount() {
    // arrange
    ProductActivityEventMessage event =
        event(
            UUID.randomUUID(),
            ProductActivityEventType.PRODUCT_ORDERED,
            Instant.parse("2026-07-16T00:00:00Z"),
            50_000L,
            3);
    when(projectionRepository.markProcessed(event.eventId())).thenReturn(true);

    // act
    processor.process(event);

    // assert
    verify(projectionRepository)
        .increment(
            LocalDate.of(2026, 7, 16), event.productId(), ProductMetricDelta.ordered(3L, 150_000L));
  }

  @DisplayName("이미 처리한 eventId는 metrics를 다시 증가시키지 않는다.")
  @Test
  void ignoresAlreadyProcessedEvent() {
    // arrange
    ProductActivityEventMessage event =
        event(
            UUID.randomUUID(),
            ProductActivityEventType.PRODUCT_LIKED,
            Instant.parse("2026-07-16T00:00:00Z"),
            null,
            null);
    when(projectionRepository.markProcessed(event.eventId())).thenReturn(false);

    // act
    boolean processed = processor.process(event);

    // assert
    assertThat(processed).isFalse();
    verify(projectionRepository, never())
        .increment(LocalDate.of(2026, 7, 16), event.productId(), ProductMetricDelta.liked());
  }

  @DisplayName("주문 수량이 유효하지 않으면 inbox 기록 전에 거부한다.")
  @Test
  void rejectsInvalidOrderBeforeInbox() {
    // arrange
    ProductActivityEventMessage event =
        event(
            UUID.randomUUID(),
            ProductActivityEventType.PRODUCT_ORDERED,
            Instant.parse("2026-07-16T00:00:00Z"),
            50_000L,
            0);

    // act & assert
    assertThrows(IllegalArgumentException.class, () -> processor.process(event));
    verify(projectionRepository, never()).markProcessed(event.eventId());
  }

  private ProductActivityEventMessage event(
      UUID eventId,
      ProductActivityEventType eventType,
      Instant occurredAt,
      Long unitPrice,
      Integer quantity) {
    return new ProductActivityEventMessage(
        eventId, 1, eventType, occurredAt, 99L, unitPrice, quantity);
  }
}
