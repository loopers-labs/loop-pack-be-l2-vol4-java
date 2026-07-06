package com.loopers.order.application.event;

import com.loopers.order.application.DataPlatformSender;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 주문 생성 이벤트의 부가 처리 핸들러.
 * - AFTER_COMMIT : 주문이 "실제 커밋된 경우에만" 데이터 플랫폼에 전송 (롤백된 주문 정보 유출 방지)
 * - @Async       : 데이터 플랫폼이 느려도 주문 응답을 막지 않음 (장애 격리)
 * - @Transactional 없음 : 외부 호출만 수행하고 DB 쓰기가 없어 트랜잭션이 불필요
 */
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final DataPlatformSender dataPlatformSender;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        dataPlatformSender.sendOrder(event);
    }
}
