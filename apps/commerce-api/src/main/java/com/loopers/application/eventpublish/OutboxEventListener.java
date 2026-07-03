package com.loopers.application.eventpublish;

import com.loopers.domain.event.CouponIssueRequestedEvent;
import com.loopers.domain.event.KafkaTopics;
import com.loopers.domain.event.LikeChangedEvent;
import com.loopers.domain.event.OrderCompletedEvent;
import com.loopers.domain.event.PaymentSettledEvent;
import com.loopers.domain.event.ProductViewedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * ApplicationEvent 를 받아 Outbox 에 저장하는 리스너.
 * <p>
 * <b>주의: @TransactionalEventListener 가 아닌 @EventListener 를 쓴다.</b>
 * Transactional Outbox 패턴은 도메인 커밋과 outbox INSERT 를 **같은 트랜잭션**에서 처리해야 원자성이 보장된다.
 * AFTER_COMMIT 으로 발행하면 도메인은 커밋됐는데 outbox 는 저장 실패할 여지가 생겨 데이터 정합성이 깨진다.
 */
@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private final OutboxAppender outboxAppender;

    @EventListener
    public void onLikeChanged(LikeChangedEvent event) {
        outboxAppender.append(
            "Product",
            event.productId().toString(),
            "LikeChanged",
            KafkaTopics.CATALOG_EVENTS,
            event.productId().toString(),
            event
        );
    }

    @EventListener
    public void onProductViewed(ProductViewedEvent event) {
        outboxAppender.append(
            "Product",
            event.productId().toString(),
            "ProductViewed",
            KafkaTopics.CATALOG_EVENTS,
            event.productId().toString(),
            event
        );
    }

    @EventListener
    public void onOrderCompleted(OrderCompletedEvent event) {
        outboxAppender.append(
            "Order",
            event.orderId().toString(),
            "OrderCompleted",
            KafkaTopics.ORDER_EVENTS,
            event.orderId().toString(),
            event
        );
    }

    @EventListener
    public void onPaymentSettled(PaymentSettledEvent event) {
        outboxAppender.append(
            "Payment",
            event.paymentId().toString(),
            "PaymentSettled",
            KafkaTopics.ORDER_EVENTS,
            event.orderId().toString(), // 파티션 키는 orderId — 같은 주문의 이벤트 순서 보장
            event
        );
    }

    @EventListener
    public void onCouponIssueRequested(CouponIssueRequestedEvent event) {
        outboxAppender.append(
            "Coupon",
            event.couponTemplateId().toString(),
            "CouponIssueRequested",
            KafkaTopics.COUPON_ISSUE_REQUESTS,
            event.couponTemplateId().toString(),
            event
        );
    }
}
