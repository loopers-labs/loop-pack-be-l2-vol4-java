package com.loopers.order.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaTopic;
import com.loopers.outbox.domain.OutboxEvent;
import com.loopers.outbox.domain.OutboxEventRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 결제 완료 이벤트를 order-events outbox 에 적재한다(결제 트랜잭션과 원자적으로).
 * consumer 는 이 트리거로 판매량(PAID 기준)을 SSOT 에서 재계산한다.
 */
@Component
@RequiredArgsConstructor
public class OrderPaidOutboxListener {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void on(OrderPaidEvent event) {
        outboxEventRepository.save(OutboxEvent.pending(
                KafkaTopic.ORDER_EVENTS,
                String.valueOf(event.orderId()), // 파티션 key = orderId
                "OrderPaid",
                serialize(event)
        ));
    }

    private String serialize(OrderPaidEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "OrderPaidEvent 직렬화 실패");
        }
    }
}
