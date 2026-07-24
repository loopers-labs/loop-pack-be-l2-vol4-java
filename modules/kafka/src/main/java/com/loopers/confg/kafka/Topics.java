package com.loopers.confg.kafka;

/**
 * Kafka 토픽 이름 상수. 프로듀서(commerce-api)와 컨슈머(commerce-streamer)가 공유한다.
 */
public final class Topics {

    private Topics() {}

    /** 상품 지표 관련 이벤트(ProductLiked/Unliked). 파티션 키 = productId. */
    public static final String CATALOG_EVENTS = "catalog-events";

    /** 주문 완료 이벤트(OrderCompleted). 파티션 키 = orderId. */
    public static final String ORDER_EVENTS = "order-events";

    /** 선착순 쿠폰 발급 요청. 파티션 키 = couponId (같은 쿠폰 요청을 한 파티션에서 순서대로 처리). */
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";
}
