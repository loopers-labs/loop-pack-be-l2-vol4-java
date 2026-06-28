package com.loopers.application.event.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.outbox.EventOutbox;
import com.loopers.domain.event.outbox.EventOutboxRepository;
import com.loopers.domain.event.order.OrderPaidEvent;
import com.loopers.domain.ordering.order.Order;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderEventPublisher {

    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public EventOutbox publishOrderPaid(Order order) {
        OrderPaidEvent event = OrderPaidEvent.from(order);
        return eventOutboxRepository.save(new EventOutbox(
            EventOutbox.TOPIC_ORDER_EVENTS,
            String.valueOf(order.getId()),
            EventOutbox.EVENT_ORDER_PAID,
            EventOutbox.AGGREGATE_ORDER,
            String.valueOf(order.getId()),
            serialize(event)
        ));
    }

    private String serialize(OrderPaidEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문 완료 이벤트 payload 생성에 실패했습니다.");
        }
    }
}
