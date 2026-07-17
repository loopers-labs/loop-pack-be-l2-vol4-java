package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class CatalogEventsConsumerTest {

    @Autowired
    private CatalogEventsConsumer catalogEventsConsumer;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;

    @Autowired
    private com.loopers.utils.RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("catalog-events를 처리할 때,")
    @Nested
    class Process {

        @DisplayName("LikedEvent가 오면 product_metrics.like_count가 1 증가한다.")
        @Test
        void process_incrementsLikeCount_whenLiked() throws Exception {
            String payload = "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";

            catalogEventsConsumer.process("event-1", payload);

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("UnlikedEvent가 오면 product_metrics.like_count가 1 감소한다.")
        @Test
        void process_decrementsLikeCount_whenUnliked() throws Exception {
            catalogEventsConsumer.process("event-1", "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}");
            catalogEventsConsumer.process("event-2", "{\"eventType\":\"UnlikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}");

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(0);
        }

        @DisplayName("ProductViewedEvent가 오면 product_metrics.view_count가 1 증가한다.")
        @Test
        void process_incrementsViewCount_whenProductViewed() throws Exception {
            String payload = "{\"eventType\":\"ProductViewedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";

            catalogEventsConsumer.process("event-1", payload);

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getViewCount()).isEqualTo(1);
        }

        @DisplayName("동일한 event_id가 다시 오면 중복 처리되지 않는다.")
        @Test
        void process_skips_whenEventAlreadyHandled() throws Exception {
            String payload = "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";

            catalogEventsConsumer.process("event-1", payload);
            catalogEventsConsumer.process("event-1", payload);

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getLikeCount()).isEqualTo(1);
        }

        @DisplayName("처리 완료 후 event_handled 테이블에 event_id가 저장된다.")
        @Test
        void process_savesEventHandled() throws Exception {
            String payload = "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";

            catalogEventsConsumer.process("event-1", payload);

            assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue();
        }

        @DisplayName("ProductViewedEvent가 오면 오늘 랭킹 key에 0.1점이 반영된다.")
        @Test
        void process_addsViewScore_whenProductViewed() throws Exception {
            String payload = "{\"eventType\":\"ProductViewedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";
            String todayKey = "ranking:all:" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

            catalogEventsConsumer.process("event-1", payload);

            Double score = redisTemplate.opsForZSet().score(todayKey, "10");
            assertThat(score).isEqualTo(0.1);
        }

        @DisplayName("LikedEvent가 오면 오늘 랭킹 key에 0.2점이 반영된다.")
        @Test
        void process_addsLikeScore_whenLiked() throws Exception {
            String payload = "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}";
            String todayKey = "ranking:all:" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

            catalogEventsConsumer.process("event-1", payload);

            Double score = redisTemplate.opsForZSet().score(todayKey, "10");
            assertThat(score).isEqualTo(0.2);
        }

        @DisplayName("UnlikedEvent가 오면 오늘 랭킹 key에서 0.2점이 차감된다.")
        @Test
        void process_subtractsLikeScore_whenUnliked() throws Exception {
            String todayKey = "ranking:all:" + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
            catalogEventsConsumer.process("event-1", "{\"eventType\":\"LikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}");

            catalogEventsConsumer.process("event-2", "{\"eventType\":\"UnlikedEvent\",\"data\":{\"productId\":10,\"memberId\":1}}");

            Double score = redisTemplate.opsForZSet().score(todayKey, "10");
            assertThat(score).isEqualTo(0.0);
        }
    }
}
