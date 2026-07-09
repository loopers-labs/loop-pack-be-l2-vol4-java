package com.loopers.interfaces.consumer;

/**
 * catalog-events 로 전파되는 상품 카탈로그 사실의 종류. producer(commerce-api)의 동명 enum 과 <b>값 이름이 계약</b>이며,
 * 공유 jar 대신 각자 사본을 갖는다(서비스 간 코드 결합 회피). collector 는 이 타입으로 어느 카운터를 갱신할지 분기한다.
 */
public enum CatalogEventType {
    PRODUCT_LIKED,
    PRODUCT_UNLIKED,
    /** 상품 조회(유실 허용 분석 신호, producer 가 outbox 아닌 직접 발행으로 보냄). */
    PRODUCT_VIEWED
}
