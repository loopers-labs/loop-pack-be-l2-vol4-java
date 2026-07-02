package com.loopers.infrastructure.outbox;

/**
 * order-events 토픽으로 전파되는 주문/결제 결과 사실의 종류.
 * streamer 가 이 타입으로 product_metrics 의 어느 카운터를 갱신할지 분기한다.
 */
public enum OrderEventType {
    /** 결제 성공으로 확정 판매된 상품 라인아이템 1건(수량 포함). */
    PRODUCT_SOLD
}
