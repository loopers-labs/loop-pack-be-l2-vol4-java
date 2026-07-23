package com.loopers.tddstudy.application.ranking;

import com.loopers.tddstudy.messaging.CatalogEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"catalog-events"})
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class RankingPipelineIntegrationTest {

    // 발생 시각을 고정 → 어느 날짜 판(키)에 들어갈지 결정적
    private static final LocalDate DATE = LocalDate.of(2026, 7, 10);
    private static final long OCCURRED_AT = DATE.atStartOfDay(ZoneId.of("Asia/Seoul"))
            .plusHours(12).toInstant().toEpochMilli();
    private static final String KEY = "ranking:all:20260710";

    @Autowired KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired StringRedisTemplate redis;

    @AfterEach
    void tearDown() {
        redis.delete(KEY);
    }

    @Test
    void 좋아요_이벤트를_소비하면_해당_날짜_ZSET에_점수가_반영되고_중복은_가산되지_않는다() throws Exception {
        CatalogEvent event = new CatalogEvent("rank-evt-1", "PRODUCT_LIKED", 777L, 1, OCCURRED_AT);

        kafkaTemplate.send(CatalogEvent.TOPIC, String.valueOf(event.productId()), event);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            Double score = redis.opsForZSet().score(KEY, "777");
            assertThat(score).isEqualTo(0.2);   // PRODUCT_LIKED = +0.2
        });

        // 같은 event_id 재전송 → 멱등 skip → 발행 안 됨 → 점수 그대로
        kafkaTemplate.send(CatalogEvent.TOPIC, String.valueOf(event.productId()), event);
        Thread.sleep(3000);
        assertThat(redis.opsForZSet().score(KEY, "777")).isEqualTo(0.2);
    }
}
