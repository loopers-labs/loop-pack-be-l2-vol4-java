package com.loopers.application.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.coupon.event.CouponIssueRequestedEvent;
import com.loopers.domain.like.event.ProductLikedEvent;
import com.loopers.domain.like.event.ProductUnlikedEvent;
import com.loopers.domain.outbox.OutboxEventModel;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.payment.event.PaymentCompletedEvent;
import com.loopers.domain.product.event.ProductViewedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class OutboxEventListenerTest {

    @Mock private OutboxEventRepository outboxEventRepository;

    private OutboxEventListener listener;

    private static final Long USER_ID = 1L;
    private static final Long PRODUCT_ID = 10L;
    private static final Long PAYMENT_ID = 100L;
    private static final Long ORDER_ID = 1000L;
    private static final Long REQUEST_ID = 10000L;
    private static final Long COUPON_ID = 5L;

    @BeforeEach
    void setUp() {
        listener = new OutboxEventListener(outboxEventRepository, new ObjectMapper());
    }

    @DisplayName("ProductLikedEvent 수신 시,")
    @Nested
    class OnProductLiked {

        @DisplayName("catalog-events 토픽, productId를 key로 하는 outbox 레코드가 저장된다.")
        @Test
        void savesOutboxEvent_withCatalogTopicAndProductIdKey() {
            // arrange
            ArgumentCaptor<OutboxEventModel> captor = ArgumentCaptor.forClass(OutboxEventModel.class);
            given(outboxEventRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new ProductLikedEvent(USER_ID, PRODUCT_ID));

            // assert
            OutboxEventModel saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(saved.getMessageKey()).isEqualTo(PRODUCT_ID.toString());
            assertThat(saved.getEventId()).isNotBlank();
            assertThat(saved.getPayload())
                .contains("\"eventId\":\"" + saved.getEventId() + "\"")
                .contains("\"eventType\":\"PRODUCT_LIKED\"")
                .contains("\"userId\":" + USER_ID)
                .contains("\"productId\":" + PRODUCT_ID);
        }

        @DisplayName("저장 중 예외가 발생하면 상위(도메인 트랜잭션)로 전파된다 — outbox는 도메인 변경과 원자적이어야 한다.")
        @Test
        void propagatesException_whenSaveFails() {
            // arrange
            willThrow(new RuntimeException("DB 오류")).given(outboxEventRepository).save(any());

            // act & assert
            assertThrows(RuntimeException.class, () -> listener.on(new ProductLikedEvent(USER_ID, PRODUCT_ID)));
        }
    }

    @DisplayName("ProductUnlikedEvent 수신 시,")
    @Nested
    class OnProductUnliked {

        @DisplayName("catalog-events 토픽에 PRODUCT_UNLIKED 페이로드로 저장된다.")
        @Test
        void savesOutboxEvent_withUnlikedPayload() {
            // arrange
            ArgumentCaptor<OutboxEventModel> captor = ArgumentCaptor.forClass(OutboxEventModel.class);
            given(outboxEventRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new ProductUnlikedEvent(USER_ID, PRODUCT_ID));

            // assert
            OutboxEventModel saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(saved.getPayload()).contains("\"eventType\":\"PRODUCT_UNLIKED\"");
        }
    }

    @DisplayName("ProductViewedEvent 수신 시,")
    @Nested
    class OnProductViewed {

        @DisplayName("catalog-events 토픽에 userId 없이(null) PRODUCT_VIEWED 페이로드로 저장된다.")
        @Test
        void savesOutboxEvent_withNullUserId() {
            // arrange
            ArgumentCaptor<OutboxEventModel> captor = ArgumentCaptor.forClass(OutboxEventModel.class);
            given(outboxEventRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new ProductViewedEvent(PRODUCT_ID));

            // assert
            OutboxEventModel saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo(KafkaTopics.CATALOG_EVENTS);
            assertThat(saved.getMessageKey()).isEqualTo(PRODUCT_ID.toString());
            assertThat(saved.getPayload())
                .contains("\"eventType\":\"PRODUCT_VIEWED\"")
                .contains("\"userId\":null")
                .contains("\"productId\":" + PRODUCT_ID);
        }
    }

    @DisplayName("PaymentCompletedEvent 수신 시,")
    @Nested
    class OnPaymentCompleted {

        @DisplayName("order-events 토픽, orderId를 key로 하는 outbox 레코드가 상품별 수량과 함께 저장된다.")
        @Test
        void savesOutboxEvent_withOrderTopicAndItems() {
            // arrange
            ArgumentCaptor<OutboxEventModel> captor = ArgumentCaptor.forClass(OutboxEventModel.class);
            given(outboxEventRepository.save(captor.capture())).willReturn(null);
            List<PaymentCompletedEvent.Item> items = List.of(
                new PaymentCompletedEvent.Item(PRODUCT_ID, 2),
                new PaymentCompletedEvent.Item(PRODUCT_ID + 1, 1)
            );

            // act
            listener.on(new PaymentCompletedEvent(PAYMENT_ID, ORDER_ID, USER_ID, items));

            // assert
            OutboxEventModel saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo(KafkaTopics.ORDER_EVENTS);
            assertThat(saved.getMessageKey()).isEqualTo(ORDER_ID.toString());
            assertThat(saved.getPayload())
                .contains("\"eventType\":\"ORDER_PAID\"")
                .contains("\"orderId\":" + ORDER_ID)
                .contains("\"productId\":" + PRODUCT_ID + ",\"quantity\":2")
                .contains("\"productId\":" + (PRODUCT_ID + 1) + ",\"quantity\":1");
        }
    }

    @DisplayName("CouponIssueRequestedEvent 수신 시,")
    @Nested
    class OnCouponIssueRequested {

        @DisplayName("coupon-issue-requests 토픽, couponId를 key로 하는 outbox 레코드가 저장된다.")
        @Test
        void savesOutboxEvent_withCouponIssueRequestsTopic() {
            // arrange
            ArgumentCaptor<OutboxEventModel> captor = ArgumentCaptor.forClass(OutboxEventModel.class);
            given(outboxEventRepository.save(captor.capture())).willReturn(null);

            // act
            listener.on(new CouponIssueRequestedEvent(REQUEST_ID, USER_ID, COUPON_ID));

            // assert
            OutboxEventModel saved = captor.getValue();
            assertThat(saved.getTopic()).isEqualTo(KafkaTopics.COUPON_ISSUE_REQUESTS);
            assertThat(saved.getMessageKey()).isEqualTo(COUPON_ID.toString());
            assertThat(saved.getPayload())
                .contains("\"eventType\":\"ISSUE_REQUESTED\"")
                .contains("\"requestId\":" + REQUEST_ID)
                .contains("\"userId\":" + USER_ID)
                .contains("\"couponId\":" + COUPON_ID);
        }
    }
}
