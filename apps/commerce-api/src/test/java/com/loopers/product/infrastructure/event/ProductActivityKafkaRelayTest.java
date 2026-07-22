package com.loopers.product.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.product.application.event.ProductActivityEvent;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class ProductActivityKafkaRelayTest {

  private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
  private final ProductActivityKafkaRelay relay =
      new ProductActivityKafkaRelay(kafkaTemplate, ProductActivityEvent.TOPIC);

  @DisplayName("상품 ID를 key로 사용해 행동 이벤트를 Kafka에 비동기 전송한다.")
  @Test
  void sendsEventWithProductIdKey() {
    ProductActivityEvent event = ProductActivityEvent.viewed(10L);
    CompletableFuture<SendResult<Object, Object>> result = CompletableFuture.completedFuture(null);
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event)).thenReturn(result);

    relay.relay(event);

    verify(kafkaTemplate).send(ProductActivityEvent.TOPIC, "10", event);
  }

  @DisplayName("트랜잭션 커밋 후 발행하며 트랜잭션이 없는 호출도 처리한다.")
  @Test
  void relaysAfterCommitWithFallback() throws NoSuchMethodException {
    Method relayMethod =
        ProductActivityKafkaRelay.class.getDeclaredMethod("relay", ProductActivityEvent.class);
    TransactionalEventListener listener =
        relayMethod.getAnnotation(TransactionalEventListener.class);

    assertThat(listener).isNotNull();
    assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(listener.fallbackExecution()).isTrue();
  }

  @DisplayName("Kafka 비동기 전송 실패는 기존 요청에 예외를 전파하지 않는다.")
  @Test
  void doesNotPropagateAsynchronousFailure() {
    ProductActivityEvent event = ProductActivityEvent.liked(10L);
    CompletableFuture<SendResult<Object, Object>> result = new CompletableFuture<>();
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event)).thenReturn(result);

    assertDoesNotThrow(
        () -> {
          relay.relay(event);
          result.completeExceptionally(new IllegalStateException("broker unavailable"));
        });
  }

  @DisplayName("Kafka 전송 호출 자체가 실패해도 기존 요청에 예외를 전파하지 않는다.")
  @Test
  void doesNotPropagateSynchronousFailure() {
    ProductActivityEvent event = ProductActivityEvent.liked(10L);
    when(kafkaTemplate.send(ProductActivityEvent.TOPIC, "10", event))
        .thenThrow(new IllegalStateException("producer unavailable"));

    assertDoesNotThrow(() -> relay.relay(event));
  }
}
