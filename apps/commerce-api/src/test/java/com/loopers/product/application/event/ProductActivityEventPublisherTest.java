package com.loopers.product.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProductActivityEventPublisherTest {

  private final ApplicationEventPublisher applicationEventPublisher =
      mock(ApplicationEventPublisher.class);
  private final ProductActivityEventPublisher publisher =
      new ProductActivityEventPublisher(applicationEventPublisher);

  @DisplayName("상품 조회 이벤트는 공통 wire 계약의 필수 필드를 포함한다.")
  @Test
  void publishesViewedEvent() {
    Long productId = 1L;
    var eventCaptor = org.mockito.ArgumentCaptor.forClass(ProductActivityEvent.class);

    publisher.publishViewed(productId);

    verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
    ProductActivityEvent event = eventCaptor.getValue();
    assertAll(
        () -> assertThat(event.eventId()).isNotNull(),
        () -> assertThat(event.version()).isEqualTo(ProductActivityEvent.CURRENT_VERSION),
        () -> assertThat(event.eventType()).isEqualTo(ProductActivityEventType.PRODUCT_VIEWED),
        () -> assertThat(event.occurredAt()).isNotNull(),
        () -> assertThat(event.productId()).isEqualTo(productId),
        () -> assertThat(event.unitPrice()).isNull(),
        () -> assertThat(event.quantity()).isNull());
  }

  @DisplayName("애플리케이션 이벤트 리스너가 실패해도 기존 요청에는 예외를 전파하지 않는다.")
  @Test
  void doesNotPropagateListenerFailure() {
    doThrow(new IllegalStateException("listener failure"))
        .when(applicationEventPublisher)
        .publishEvent(any(ProductActivityEvent.class));

    assertDoesNotThrow(() -> publisher.publishLiked(1L));
  }
}
