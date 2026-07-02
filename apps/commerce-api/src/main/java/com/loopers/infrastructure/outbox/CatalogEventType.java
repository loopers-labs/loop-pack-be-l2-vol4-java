package com.loopers.infrastructure.outbox;

/**
 * catalog-events 토픽으로 전파되는 상품 카탈로그 관련 사실의 종류.
 * streamer 가 이 타입으로 product_metrics 의 어느 카운터를 갱신할지 분기한다.
 */
public enum CatalogEventType {
    PRODUCT_LIKED,
    PRODUCT_UNLIKED,
    /** 상품 상세 조회. 유실 허용 분석 신호라 outbox 가 아닌 직접 발행 경로로 온다(상태변경 없는 읽기라 원자성 대상 부재). */
    PRODUCT_VIEWED
}
