package com.loopers.application.event.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import com.loopers.domain.event.order.OrderPaidEvent;
import com.loopers.domain.ordering.order.Order;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderEventPublisher {

    private final OrderEventOutboxRepository orderEventOutboxRepository;
    private final ObjectMapper objectMapper;

    public OrderEventOutbox publishOrderPaid(Order order) {
        OrderPaidEvent event = OrderPaidEvent.from(order);
        return orderEventOutboxRepository.save(new OrderEventOutbox(
            order.getId(),
            OrderEventOutbox.ORDER_PAID,
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
