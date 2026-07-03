package com.loopers.domain.event;

/**
 * 프로젝트 전역 Kafka 토픽 상수.
 * <p>
 * 파티션 키 결정 원칙:
 * - catalog-events: productId — 같은 상품에 대한 like/view 는 순서가 중요
 * - order-events: orderId — 주문/결제 이벤트 순서 보장
 * - coupon-issue-requests: couponTemplateId — 같은 쿠폰은 단일 파티션에서 순차 처리 (동시성 제어)
 */
public final class KafkaTopics {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private KafkaTopics() {}
}
