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
class OrderEventsConsumerTest {

    @Autowired
    private OrderEventsConsumer orderEventsConsumer;

    @Autowired
    private ProductMetricsJpaRepository productMetricsJpaRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
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
                          {"productId": 10, "quantity": 2},
                          {"productId": 20, "quantity": 3}
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
                        "items": [{"productId": 10, "quantity": 1}]
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
                        "items": [{"productId": 10, "quantity": 1}]
                      }
                    }
                    """;

            orderEventsConsumer.process("event-1", payload);

            assertThat(eventHandledJpaRepository.existsById("event-1")).isTrue();
        }
    }
}
