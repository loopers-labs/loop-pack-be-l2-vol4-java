package com.loopers.infrastructure.outbox;

/**
 * week7 이벤트 토픽 이름 상수. 발행측(commerce-api)과 소비측(commerce-streamer)이 동일 문자열을 써야 한다.
 *
 * <ul>
 *   <li>{@link #CATALOG_EVENTS} key=productId — 좋아요 변경(LIKE_CHANGED), 상품 조회(PRODUCT_VIEWED)</li>
 *   <li>{@link #ORDER_EVENTS} key=orderId — 결제 확정(ORDER_PAID) 등 주문 이벤트</li>
 *   <li>{@link #COUPON_ISSUE_REQUESTS} key=couponId — 선착순 쿠폰 발급 요청(COUPON_ISSUE_REQUESTED)</li>
 * </ul>
 */
public final class EventTopics {

    public static final String CATALOG_EVENTS = "catalog-events";
    public static final String ORDER_EVENTS = "order-events";
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    private EventTopics() {}
}
