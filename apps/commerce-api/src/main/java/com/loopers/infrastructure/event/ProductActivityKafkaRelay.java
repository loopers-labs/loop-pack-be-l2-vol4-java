package com.loopers.infrastructure.event;

import com.loopers.application.event.ProductActivityEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductActivityKafkaRelay {

  private final KafkaTemplate<Object, Object> kafkaTemplate;
  private final String topic;

  public ProductActivityKafkaRelay(
      KafkaTemplate<Object, Object> kafkaTemplate,
      @Value("${commerce.kafka.topics.product-activity}") String topic) {
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
  }

  @EventListener
  public void relay(ProductActivityEvent event) {
    try {
      kafkaTemplate
          .send(topic, event.productId().toString(), event)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  logFailure(event, exception);
                }
              });
    } catch (RuntimeException exception) {
      logFailure(event, exception);
    }
  }

  private void logFailure(ProductActivityEvent event, Throwable exception) {
    log.warn(
        "상품 행동 Kafka 이벤트 발행에 실패했습니다. eventId={}, eventType={}, productId={}",
        event.eventId(),
        event.eventType(),
        event.productId(),
        exception);
  }
}
