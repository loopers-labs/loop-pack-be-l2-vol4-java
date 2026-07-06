package com.loopers.order.infrastructure;

import com.loopers.order.application.DataPlatformSender;
import com.loopers.order.application.event.OrderCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 실제 데이터 플랫폼 연동 전, 전송 사실만 로깅하는 stub 구현.
 */
@Slf4j
@Component
public class LoggingDataPlatformSender implements DataPlatformSender {

    @Override
    public void sendOrder(OrderCreatedEvent event) {
        log.info("[데이터 플랫폼] 주문 정보 전송 orderId={} userId={} orderNumber={} finalAmount={} orderedAt={}",
                event.orderId(), event.userId(), event.orderNumber(), event.finalAmount(), event.orderedAt());
    }
}
