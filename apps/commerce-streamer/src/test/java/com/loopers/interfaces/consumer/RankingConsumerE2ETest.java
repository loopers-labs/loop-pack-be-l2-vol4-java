package com.loopers.interfaces.consumer;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.testcontainers.KafkaTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

@SpringBootTest
class RankingConsumerE2ETest {

    // occurredAt "2026-07-15T03:00:00Z" → KST 12:00 → 일간 키 20260715 로 결정적이다.
    private static final String OCCURRED_AT = "2026-07-15T03:00:00Z";
    private static final LocalDate DATE = LocalDate.of(2026, 7, 15);

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KafkaTestContainersConfig::getBootstrapServers);
    }

    @Autowired private RankingRepository rankingRepository;
    @Autowired private DatabaseCleanUp databaseCleanUp;
    @Autowired private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables(); // product-metrics 그룹도 같은 토픽을 소비하므로 DB 도 정리
        redisCleanUp.truncateAll();
    }

    @DisplayName("LikeAdded 를 발행하면 일간 ZSET 에 +0.2 가 반영되고, 같은 eventId 재발행은 멱등이다.")
    @Test
    void likeEventScoredIdempotently() {
        String payload = "{\"eventId\":\"rk-e1\",\"type\":\"LikeAdded\",\"productId\":101,"
            + "\"likeCount\":1,\"version\":1,\"occurredAt\":\"" + OCCURRED_AT + "\"}";

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            publish("catalog-events", "101", payload);
            assertThat(rankingRepository.findScore(DATE, 101L)).hasValueSatisfying(
                s -> assertThat(s).isCloseTo(0.2, within(1e-9))); // 재발행에도 0.2 고정 = dedup
        });
    }

    @DisplayName("가중치가 순서에 반영된다 — 주문 1건(qty1, 0.6)이 좋아요 2건(0.4)보다 높은 점수다.")
    @Test
    void orderOutweighsTwoLikes() {
        String like1 = "{\"eventId\":\"rk-l1\",\"type\":\"LikeAdded\",\"productId\":201,"
            + "\"likeCount\":1,\"version\":1,\"occurredAt\":\"" + OCCURRED_AT + "\"}";
        String like2 = "{\"eventId\":\"rk-l2\",\"type\":\"LikeAdded\",\"productId\":201,"
            + "\"likeCount\":2,\"version\":2,\"occurredAt\":\"" + OCCURRED_AT + "\"}";
        String order = "{\"eventId\":\"rk-o1\",\"type\":\"OrderPlaced\",\"orderId\":1,"
            + "\"lines\":[{\"productId\":202,\"quantity\":1}],\"occurredAt\":\"" + OCCURRED_AT + "\"}";

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            publish("catalog-events", "201", like1);
            publish("catalog-events", "201", like2);
            publish("order-events", "1", order);
            assertThat(rankingRepository.findScore(DATE, 201L)).hasValueSatisfying(
                s -> assertThat(s).isCloseTo(0.4, within(1e-9)));
            assertThat(rankingRepository.findScore(DATE, 202L)).hasValueSatisfying(
                s -> assertThat(s).isCloseTo(0.6, within(1e-9)));
        });
    }

    private void publish(String topic, String key, String value) {
        Properties p = new Properties();
        p.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KafkaTestContainersConfig.getBootstrapServers());
        p.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        p.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(p)) {
            producer.send(new ProducerRecord<>(topic, key, value));
            producer.flush();
        }
    }
}
