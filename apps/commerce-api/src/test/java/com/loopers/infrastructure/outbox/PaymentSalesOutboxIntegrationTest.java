package com.loopers.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderItems;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.Money;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제 성공 1건이 outbox 에서 <b>상품별 order-events 행 N개</b>로 분해되는지 검증한다(결정 우2-②·③).
 *
 * <p>onPaymentCompleted 는 BEFORE_COMMIT 리스너라 발행 트랜잭션 안에서 주문을 재조회해 라인아이템으로 분해한다.
 * TxHarness 로 PaymentCompletedEvent 를 커밋 트랜잭션에서 발행하고, 저장된 outbox 행을 관찰한다:
 * 상품 수만큼 행이 생기고, 각 행의 key=productId, payload 에 해당 상품의 수량이 담겨야 한다.</p>
 */
@SpringBootTest
class PaymentSalesOutboxIntegrationTest {

    private static final long USER_ID = 77L;
    private static final long PRODUCT_A = 501L;
    private static final long PRODUCT_B = 502L;
    private static final int QTY_A = 2;
    private static final int QTY_B = 3;

    @Autowired
    private TxHarness txHarness;

    @Autowired
    private OrderJpaRepository orderJpaRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("결제 성공 이벤트는 주문 라인아이템 수만큼 order-events 행으로 분해되고, 각 행은 productId 키 + 해당 수량을 담는다.")
    @Test
    void paymentCompleted_fansOutToPerProductOrderEventRows() {
        Order order = orderJpaRepository.save(Order.create(
                USER_ID,
                OrderItems.from(List.of(
                        OrderItem.of(PRODUCT_A, "상품A", Money.of(1_000L), QTY_A),
                        OrderItem.of(PRODUCT_B, "상품B", Money.of(2_000L), QTY_B))),
                Money.of(0L)));

        txHarness.publishThenCommit(new PaymentCompletedEvent(order.getId(), USER_ID, 8_000L, ZonedDateTime.now()));

        List<OrderEventMessage> messages = outboxEventJpaRepository.findAll().stream()
                .filter(e -> e.getTopic().equals(com.loopers.interfaces.api.config.KafkaTopicConfig.ORDER_EVENTS))
                .map(e -> deserialize(e.getPayload()))
                .collect(Collectors.toList());

        assertThat(messages).hasSize(2);
        assertThat(messages).allSatisfy(m -> {
            assertThat(m.type()).isEqualTo(OrderEventType.PRODUCT_SOLD);
            assertThat(m.orderId()).isEqualTo(order.getId());
        });
        Map<Long, Integer> qtyByProduct = messages.stream()
                .collect(Collectors.toMap(OrderEventMessage::productId, OrderEventMessage::quantity));
        assertThat(qtyByProduct).containsEntry(PRODUCT_A, QTY_A).containsEntry(PRODUCT_B, QTY_B);

        // 파티셔닝 키가 productId 여야 상품 단위 파티션 직렬화가 유지된다.
        List<String> keys = outboxEventJpaRepository.findAll().stream()
                .filter(e -> e.getTopic().equals(com.loopers.interfaces.api.config.KafkaTopicConfig.ORDER_EVENTS))
                .map(OutboxEvent::getAggregateId)
                .collect(Collectors.toList());
        assertThat(keys).containsExactlyInAnyOrder(String.valueOf(PRODUCT_A), String.valueOf(PRODUCT_B));
    }

    private OrderEventMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, OrderEventMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("order-events payload 역직렬화 실패: " + payload, e);
        }
    }

    @TestConfiguration
    static class TxHarnessConfig {
        @Bean
        TxHarness txHarness(ApplicationEventPublisher publisher) {
            return new TxHarness(publisher);
        }
    }

    /** 이벤트를 커밋 트랜잭션 경계 안에서 발행하는 하네스(별도 빈이라 프록시로 @Transactional 적용). */
    static class TxHarness {
        private final ApplicationEventPublisher publisher;

        TxHarness(ApplicationEventPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        public void publishThenCommit(Object event) {
            publisher.publishEvent(event);
        }
    }
}
