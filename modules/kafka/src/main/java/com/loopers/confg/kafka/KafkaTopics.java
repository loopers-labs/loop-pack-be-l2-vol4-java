package com.loopers.confg.kafka;

public final class KafkaTopics {

    // 상품/재고/좋아요 이벤트, key=productId
    public static final String CATALOG_EVENTS = "catalog-events";

    // 주문/결제 이벤트, key=orderId
    public static final String ORDER_EVENTS = "order-events";

    // 쿠폰 발급 요청, key=couponId
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private KafkaTopics() {}
}
