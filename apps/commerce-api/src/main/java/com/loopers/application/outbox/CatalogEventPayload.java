package com.loopers.application.outbox;

/**
 * catalog-events 토픽에 발행되는 메시지 페이로드.
 * eventType으로 Consumer가 좋아요/좋아요 취소 등 행동을 구분하고,
 * eventId로 Consumer의 event_handled 멱등 처리를 지원한다.
 */
record CatalogEventPayload(String eventId, String eventType, Long userId, Long productId) {

    static final String PRODUCT_LIKED = "PRODUCT_LIKED";
    static final String PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    // 상품 상세 조회 API는 비인증(조회 전용)이라 userId를 알 수 없으므로 null로 발행된다.
    static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
}
