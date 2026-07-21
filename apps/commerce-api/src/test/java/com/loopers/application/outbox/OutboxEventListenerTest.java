package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponIssueRequestedEvent;
import com.loopers.application.like.LikeChangedEvent;
import com.loopers.application.payment.PaymentCompletedEvent;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.order.OrderLine;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxEventListener 단위 테스트.
 *
 * <p>Step 1 이벤트가 outbox 레코드(토픽/파티션키/EventEnvelope payload)로 올바르게 변환되는지 검증한다.
 * BEFORE_COMMIT 참여(트랜잭션 원자성) 자체는 통합 테스트의 몫이고, 여기선 변환 규칙만 본다.
 */
class OutboxEventListenerTest {

    private OutboxEventRepository outboxEventRepository;
    private OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private OutboxEventListener sut;

    @BeforeEach
    void setUp() {
        outboxEventRepository = mock(OutboxEventRepository.class);
        orderRepository = mock(OrderRepository.class);
        sut = new OutboxEventListener(outboxEventRepository, orderRepository, objectMapper);
        ReflectionTestUtils.setField(sut, "catalogTopic", "catalog-events");
        ReflectionTestUtils.setField(sut, "orderTopic", "order-events");
        ReflectionTestUtils.setField(sut, "couponIssueTopic", "coupon-issue-requests");
    }

    @DisplayName("좋아요 이벤트는 catalog 토픽, productId 파티션 키, LIKE_CHANGED 봉투로 기록된다.")
    @Test
    void writesLikeChangedOutbox() throws Exception {
        // act
        sut.onLikeChanged(LikeChangedEvent.liked(100L));

        // assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("catalog-events");
        assertThat(saved.getPartitionKey()).isEqualTo("100");
        assertThat(saved.getEventType()).isEqualTo("LIKE_CHANGED");
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);

        JsonNode envelope = objectMapper.readTree(saved.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(saved.getEventId());
        assertThat(envelope.get("eventType").asText()).isEqualTo("LIKE_CHANGED");
        assertThat(envelope.get("payload").get("productId").asLong()).isEqualTo(100L);
        assertThat(envelope.get("payload").get("type").asText()).isEqualTo("LIKED");
    }

    @DisplayName("결제 완료 이벤트는 주문 항목(productId/quantity)을 payload 에 실어 order 토픽으로 기록된다.")
    @Test
    void writesPaymentCompletedOutboxWithOrderItems() throws Exception {
        // arrange — 실제 OrderService 로 주문(항목 2종) 구성
        ProductModel productA = new ProductModel(1L, "신발", "러닝화", 10_000L);
        ProductModel productB = new ProductModel(1L, "가방", "백팩", 20_000L);
        ReflectionTestUtils.setField(productA, "id", 100L);
        ReflectionTestUtils.setField(productB, "id", 200L);
        OrderModel order = new OrderService().createOrder(1L, List.of(
            new OrderLine(productA, StockModel.of(100L, 10), 2),
            new OrderLine(productB, StockModel.of(200L, 10), 1)
        ));
        when(orderRepository.findById(77L)).thenReturn(Optional.of(order));

        // act
        sut.onPaymentCompleted(new PaymentCompletedEvent(77L, "TX-1", 40_000L));

        // assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("order-events");
        assertThat(saved.getPartitionKey()).isEqualTo("77");
        assertThat(saved.getEventType()).isEqualTo("PAYMENT_COMPLETED");

        JsonNode payload = objectMapper.readTree(saved.getPayload()).get("payload");
        assertThat(payload.get("orderId").asLong()).isEqualTo(77L);
        assertThat(payload.get("transactionKey").asText()).isEqualTo("TX-1");
        assertThat(payload.get("amount").asLong()).isEqualTo(40_000L);
        assertThat(payload.get("items")).hasSize(2);
        // 랭킹 점수(price*amount) 계산을 위해 단가가 항목에 실린다.
        JsonNode firstItem = payload.get("items").get(0);
        assertThat(firstItem.get("productId").asLong()).isEqualTo(100L);
        assertThat(firstItem.get("quantity").asInt()).isEqualTo(2);
        assertThat(firstItem.get("price").asLong()).isEqualTo(10_000L);
    }

    @DisplayName("쿠폰 발급 이벤트는 coupon topic, userId partition key, requestId eventId로 기록한다.")
    @Test
    void writesCouponIssueOutboxWithUserIdKey() throws Exception {
        // act
        sut.onCouponIssueRequested(new CouponIssueRequestedEvent("request-1", 10L, 100L));

        // assert
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent saved = captor.getValue();

        assertThat(saved.getTopic()).isEqualTo("coupon-issue-requests");
        assertThat(saved.getPartitionKey()).isEqualTo("10");
        assertThat(saved.getEventType()).isEqualTo("COUPON_ISSUE_RESERVED");
        assertThat(saved.getEventId()).isEqualTo("request-1");

        JsonNode envelope = objectMapper.readTree(saved.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo("request-1");
        assertThat(envelope.get("eventType").asText()).isEqualTo("COUPON_ISSUE_RESERVED");
        assertThat(envelope.get("payload").get("requestId").asText()).isEqualTo("request-1");
        assertThat(envelope.get("payload").get("userId").asLong()).isEqualTo(10L);
        assertThat(envelope.get("payload").get("couponId").asLong()).isEqualTo(100L);
    }
}
