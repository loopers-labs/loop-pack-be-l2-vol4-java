package com.loopers.interfaces.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.config.kafka.RankingKafkaConfig;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.ranking.RankingKeyPolicy;
import com.loopers.interfaces.consumer.message.ProductActivityEventMessage;
import com.loopers.interfaces.consumer.message.ProductActivityEventType;
import com.loopers.utils.RedisCleanUp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class RankingKafkaConsumerIntegrationTest {

  private final KafkaTemplate<Object, Object> kafkaTemplate;
  private final RedisTemplate<String, String> masterRedisTemplate;
  private final RedisCleanUp redisCleanUp;

  @Autowired
  RankingKafkaConsumerIntegrationTest(
      KafkaTemplate<Object, Object> kafkaTemplate,
      @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
          RedisTemplate<String, String> masterRedisTemplate,
      RedisCleanUp redisCleanUp) {
    this.kafkaTemplate = kafkaTemplate;
    this.masterRedisTemplate = masterRedisTemplate;
    this.redisCleanUp = redisCleanUp;
  }

  @AfterEach
  void tearDown() {
    redisCleanUp.truncateAll();
  }

  @DisplayName("Kafka 이벤트는 유형별 가중치로 ZSET에 반영되고 재전달은 중복 반영되지 않는다.")
  @Test
  void consumesWeightedEventsIdempotently() throws Exception {
    Instant occurredAt = Instant.parse("2026-07-15T15:00:00Z");
    UUID viewedEventId = UUID.randomUUID();
    ProductActivityEventMessage viewed =
        event(viewedEventId, ProductActivityEventType.PRODUCT_VIEWED, occurredAt);
    ProductActivityEventMessage liked =
        event(UUID.randomUUID(), ProductActivityEventType.PRODUCT_LIKED, occurredAt);
    ProductActivityEventMessage ordered =
        event(UUID.randomUUID(), ProductActivityEventType.PRODUCT_ORDERED, occurredAt);

    send(viewed);
    send(viewed);
    send(liked);
    send(ordered);

    var keys = RankingKeyPolicy.dailyKeys(occurredAt);
    awaitScore(keys.rankingKey(), "1", 1.3D);
    awaitScore(keys.hourlyRankingKey(), "1", 1.3D);

    assertThat(masterRedisTemplate.opsForZSet().score(keys.rankingKey(), "1"))
        .isCloseTo(1.3D, org.assertj.core.data.Offset.offset(0.000_001D));
    assertThat(masterRedisTemplate.opsForZSet().score(keys.hourlyRankingKey(), "1"))
        .isCloseTo(1.3D, org.assertj.core.data.Offset.offset(0.000_001D));
  }

  private ProductActivityEventMessage event(
      UUID eventId, ProductActivityEventType eventType, Instant occurredAt) {
    return new ProductActivityEventMessage(
        eventId,
        1,
        eventType,
        occurredAt,
        1L,
        eventType == ProductActivityEventType.PRODUCT_ORDERED ? 50_000L : null,
        eventType == ProductActivityEventType.PRODUCT_ORDERED ? 3 : null);
  }

  private void send(ProductActivityEventMessage event) throws Exception {
    kafkaTemplate.send(RankingKafkaConfig.ACTIVITY_TOPIC, "1", event).get(5, TimeUnit.SECONDS);
  }

  private void awaitScore(String key, String member, double expected) throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      Double score = masterRedisTemplate.opsForZSet().score(key, member);
      if (score != null && Math.abs(score - expected) < 0.000_001D) {
        return;
      }
      Thread.sleep(100L);
    }
    throw new AssertionError("기대 점수가 제한 시간 안에 반영되지 않았습니다.");
  }
}
