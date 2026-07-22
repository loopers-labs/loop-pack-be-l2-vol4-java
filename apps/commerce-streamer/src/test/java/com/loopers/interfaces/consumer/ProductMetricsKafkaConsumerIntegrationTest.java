package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.config.kafka.RankingKafkaConfig;
import com.loopers.domain.metrics.ProductMetricModel;
import com.loopers.infrastructure.metrics.ProductMetricJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsEventInboxJpaRepository;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class ProductMetricsKafkaConsumerIntegrationTest {

  private final KafkaTemplate<Object, Object> kafkaTemplate;
  private final ProductMetricJpaRepository productMetricJpaRepository;
  private final ProductMetricsEventInboxJpaRepository eventInboxJpaRepository;
  private final DatabaseCleanUp databaseCleanUp;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  ProductMetricsKafkaConsumerIntegrationTest(
      KafkaTemplate<Object, Object> kafkaTemplate,
      ProductMetricJpaRepository productMetricJpaRepository,
      ProductMetricsEventInboxJpaRepository eventInboxJpaRepository,
      DatabaseCleanUp databaseCleanUp,
      RedisCleanUp redisCleanUp) {
    this.kafkaTemplate = kafkaTemplate;
    this.productMetricJpaRepository = productMetricJpaRepository;
    this.eventInboxJpaRepository = eventInboxJpaRepository;
    this.databaseCleanUp = databaseCleanUp;
    this.redisCleanUp = redisCleanUp;
  }

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
    redisCleanUp.truncateAll();
  }

  @DisplayName("별도 metrics consumer가 행동 이벤트를 날짜·상품별로 멱등 집계한다.")
  @Test
  void consumesEventsIntoProductMetricsIdempotently() throws Exception {
    // arrange
    Long productId = 999_999L;
    Instant occurredAt = Instant.parse("2026-07-15T15:00:00Z");
    UUID viewedEventId = UUID.randomUUID();
    ProductActivityEventMessage viewed =
        event(viewedEventId, ProductActivityEventType.PRODUCT_VIEWED, occurredAt, productId);
    ProductActivityEventMessage liked =
        event(UUID.randomUUID(), ProductActivityEventType.PRODUCT_LIKED, occurredAt, productId);
    ProductActivityEventMessage ordered =
        event(UUID.randomUUID(), ProductActivityEventType.PRODUCT_ORDERED, occurredAt, productId);

    // act
    send(viewed);
    send(viewed);
    send(liked);
    send(ordered);

    // assert
    ProductMetricModel metrics = awaitMetrics(LocalDate.of(2026, 7, 16), productId, 1L, 1L, 1L);
    assertAll(
        () -> assertThat(metrics.getViewCount()).isEqualTo(1L),
        () -> assertThat(metrics.getLikeCount()).isEqualTo(1L),
        () -> assertThat(metrics.getOrderCount()).isEqualTo(1L),
        () -> assertThat(metrics.getOrderQuantity()).isEqualTo(3L),
        () -> assertThat(metrics.getOrderAmount()).isEqualTo(150_000L),
        () ->
            assertThat(eventInboxJpaRepository.existsByEventId(viewedEventId.toString())).isTrue());
  }

  private ProductActivityEventMessage event(
      UUID eventId, ProductActivityEventType eventType, Instant occurredAt, Long productId) {
    return new ProductActivityEventMessage(
        eventId,
        1,
        eventType,
        occurredAt,
        productId,
        eventType == ProductActivityEventType.PRODUCT_ORDERED ? 50_000L : null,
        eventType == ProductActivityEventType.PRODUCT_ORDERED ? 3 : null);
  }

  private void send(ProductActivityEventMessage event) throws Exception {
    kafkaTemplate
        .send(RankingKafkaConfig.ACTIVITY_TOPIC, event.productId().toString(), event)
        .get(5, TimeUnit.SECONDS);
  }

  private ProductMetricModel awaitMetrics(
      LocalDate metricDate,
      Long productId,
      long expectedViews,
      long expectedLikes,
      long expectedOrders)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var metrics = productMetricJpaRepository.findByMetricDateAndProductId(metricDate, productId);
      if (metrics.isPresent()
          && metrics.get().getViewCount() == expectedViews
          && metrics.get().getLikeCount() == expectedLikes
          && metrics.get().getOrderCount() == expectedOrders) {
        return metrics.get();
      }
      Thread.sleep(100L);
    }
    throw new AssertionError("기대 product_metrics가 제한 시간 안에 반영되지 않았습니다.");
  }
}
