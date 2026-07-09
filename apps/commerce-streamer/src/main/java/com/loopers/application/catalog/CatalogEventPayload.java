package com.loopers.application.catalog;

/**
 * commerce-api의 catalog-events Producer가 발행하는 페이로드와 동일한 필드 계약(schema)을 갖는다.
 * 두 앱이 공유 모듈 없이 각자 정의하고 있어 필드 변경 시 양쪽을 함께 수정해야 한다 — 추후 계약 전용 공유 모듈 분리를 검토할 만하다.
 */
record CatalogEventPayload(String eventId, String eventType, Long userId, Long productId) {

    static final String PRODUCT_LIKED = "PRODUCT_LIKED";
    static final String PRODUCT_UNLIKED = "PRODUCT_UNLIKED";
    static final String PRODUCT_VIEWED = "PRODUCT_VIEWED";
}
