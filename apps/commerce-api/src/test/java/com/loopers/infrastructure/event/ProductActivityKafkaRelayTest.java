package com.loopers.infrastructure.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.application.event.ProductActivityEvent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class ProductActivityKafkaRelayTest {

  private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
  private final ProductActivityKafkaRelay relay =
      new ProductActivityKafkaRelay(kafkaTemplate, ProductActivityEvent.TOPIC);

  @DisplayName("상품 ID를 key로 사용해 행동 이벤트를 Kafka에 비동기 전송한다.")
  @Test
  void sendsEventWithProductIdKey() {
    // arrange
    ProductActivityEvent event = ProductActivityEvent.viewed(10L);
    CompletableFuture<SendResult<Object, Object>> result = CompletableFuture.completedFuture(null);
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event)).thenReturn(result);

    // act
    relay.relay(event);

    // assert
    verify(kafkaTemplate).send(ProductActivityEvent.TOPIC, "10", event);
  }

  @DisplayName("Kafka 비동기 전송 실패는 기존 요청에 예외를 전파하지 않는다.")
  @Test
  void doesNotPropagateAsynchronousFailure() {
    // arrange
    ProductActivityEvent event = ProductActivityEvent.liked(10L);
    CompletableFuture<SendResult<Object, Object>> result = new CompletableFuture<>();
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event)).thenReturn(result);

    // act & assert
    assertDoesNotThrow(
        () -> {
          relay.relay(event);
          result.completeExceptionally(new IllegalStateException("broker unavailable"));
        });
  }

  @DisplayName("Kafka 전송 호출 자체가 실패해도 기존 요청에 예외를 전파하지 않는다.")
  @Test
  void doesNotPropagateSynchronousFailure() {
    // arrange
    ProductActivityEvent event = ProductActivityEvent.liked(10L);
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event))
        .thenThrow(new IllegalStateException("producer unavailable"));

    // act & assert
    assertDoesNotThrow(() -> relay.relay(event));
  }
}
