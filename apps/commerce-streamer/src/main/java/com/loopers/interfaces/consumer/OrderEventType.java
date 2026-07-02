package com.loopers.interfaces.consumer;

/**
 * order-events 로 전파되는 주문/결제 결과 사실의 종류. producer(commerce-api)의 동명 enum 과 <b>값 이름이 계약</b>이며,
 * 공유 jar 대신 각자 사본을 갖는다. collector 는 이 타입으로 product_metrics 의 어느 카운터를 갱신할지 분기한다.
 */
public enum OrderEventType {
    PRODUCT_SOLD
}
