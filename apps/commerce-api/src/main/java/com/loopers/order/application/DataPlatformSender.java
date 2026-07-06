package com.loopers.order.application;

import com.loopers.order.application.event.OrderCreatedEvent;

/**
 * 주문 정보를 외부 데이터 플랫폼(분석/집계 시스템)으로 전송하는 포트.
 * 운영 DB 와 분리된 외부 시스템이므로 주문 트랜잭션 밖(AFTER_COMMIT)에서 호출한다.
 */
public interface DataPlatformSender {

    void sendOrder(OrderCreatedEvent event);
}
