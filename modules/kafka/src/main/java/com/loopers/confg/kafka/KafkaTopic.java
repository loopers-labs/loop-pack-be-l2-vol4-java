package com.loopers.confg.kafka;

/**
 * Kafka 토픽 이름 상수. producer(commerce-api)와 consumer(commerce-streamer)가 공유한다.
 */
public final class KafkaTopic {

    private KafkaTopic() {
    }

    /** 주문/결제 이벤트. key = orderId (주문별 순서 보장) */
    public static final String ORDER_EVENTS = "order-events";

    /** 선착순 쿠폰 발급 요청. key = couponId (같은 쿠폰을 같은 파티션에서 순차 처리) */
    public static final String COUPON_ISSUE_REQUESTS = "coupon-issue-requests";

    /** 상품 좋아요/조회 이벤트. key = productId (상품별 순서 보장 → 좋아요/취소 순서 일관) */
    public static final String CATALOG_EVENTS = "catalog-events";

    /** Dead Letter Topic 접미사. 처리 불가(역직렬화 실패 등) 메시지를 <topic>.DLT 로 격리한다. */
    public static final String DLT_SUFFIX = ".DLT";

    public static String deadLetterOf(String topic) {
        return topic + DLT_SUFFIX;
    }
}
