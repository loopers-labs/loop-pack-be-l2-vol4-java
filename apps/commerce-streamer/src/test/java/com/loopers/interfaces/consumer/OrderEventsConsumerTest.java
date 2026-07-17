package com.loopers.interfaces.consumer;

import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class OrderEventsConsumerTest {

    @Autowired
    private OrderEventsConsumer orderEventsConsumer;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    private String todayKey() {
        return "ranking:all:" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    @DisplayName("order-events를 처리할 때,")
    @Nested
    class Process {

        @DisplayName("OrderCreatedEvent가 오면 각 상품의 sales_count가 quantity만큼 증가한다.")
        @Test
        void process_incrementsSalesCount_perItem() throws Exception {
            String payload = """
                    {
                      "eventType": "OrderCreatedEvent",
                      "data": {
                        "orderId": 1,
                        "memberId": 1,
                        "totalPrice": 30000,
                        "items": [
                          {"productId": 10, "quantity": 2, "price": 10000},
                          {"productId": 20, "quantity": 3, "price": 5000}
                        ]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);

            var product10 = productMetricsJpaRepository.findById(10L).orElseThrow();
            var product20 = productMetricsJpaRepository.findById(20L).orElseThrow();
            assertThat(product10.getSalesCount()).isEqualTo(2);
            assertThat(product20.getSalesCount()).isEqualTo(3);
        }

        @DisplayName("OrderCreatedEvent가 오면 각 상품의 랭킹 점수가 0.7*log10(price*quantity+1)만큼 반영된다.")
        @Test
        void process_addsOrderScore_perItem() throws Exception {
            String payload = """
                    {
                      "eventType": "OrderCreatedEvent",
                      "data": {
                        "orderId": 1,
                        "memberId": 1,
                        "totalPrice": 30000,
                        "items": [
                          {"productId": 10, "quantity": 2, "price": 10000}
                        ]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);

            Double score = redisTemplate.opsForZSet().score(todayKey(), "10");
            assertThat(score).isCloseTo(0.7 * Math.log10(20_000 + 1), within(0.0001));
        }

        @DisplayName("동일한 event_id가 다시 오면 중복 처리되지 않는다.")
        @Test
        void process_skips_whenEventAlreadyHandled() throws Exception {
            String payload = """
                    {
                      "eventType": "OrderCreatedEvent",
                      "data": {
                        "orderId": 1,
                        "memberId": 1,
                        "totalPrice": 10000,
                        "items": [{"productId": 10, "quantity": 1, "price": 10000}]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);
            orderEventsConsumer.process("event-1", payload);

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getSalesCount()).isEqualTo(1);
        }

        @DisplayName("처리 완료 후 event_handled 테이블에 event_id가 저장된다.")
        @Test
        void process_savesEventHandled() throws Exception {
            String payload = """
                    {
                      "eventType": "OrderCreatedEvent",
                      "data": {
                        "orderId": 1,
                        "memberId": 1,
                        "totalPrice": 10000,
                        "items": [{"productId": 10, "quantity": 1, "price": 10000}]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);

            assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue();
        }

        @DisplayName("상품 item에 price 필드가 없으면 price를 0으로 기본값 처리하고 정상 처리된다.")
        @Test
        void process_handlesMissingPrice_withDefaultZero() throws Exception {
            String payload = """
                    {
                      "eventType": "OrderCreatedEvent",
                      "data": {
                        "orderId": 1,
                        "memberId": 1,
                        "totalPrice": 0,
                        "items": [
                          {"productId": 10, "quantity": 1}
                        ]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);

            var metrics = productMetricsJpaRepository.findById(10L).orElseThrow();
            assertThat(metrics.getSalesCount()).isEqualTo(1);
        }
    }
}
