package com.loopers.application.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductActivityEventPublisher {

  private final ApplicationEventPublisher applicationEventPublisher;

  public void publishViewed(Long productId) {
    publish(ProductActivityEvent.viewed(productId));
  }

  public void publishLiked(Long productId) {
    publish(ProductActivityEvent.liked(productId));
  }

  private void publish(ProductActivityEvent event) {
    try {
      applicationEventPublisher.publishEvent(event);
    } catch (RuntimeException exception) {
      log.warn(
          "상품 행동 애플리케이션 이벤트 발행에 실패했습니다. eventId={}, eventType={}, productId={}",
          event.eventId(),
          event.eventType(),
          event.productId(),
          exception);
    }
  }
}
