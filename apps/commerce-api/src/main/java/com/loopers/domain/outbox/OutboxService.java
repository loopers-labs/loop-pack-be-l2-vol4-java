package com.loopers.domain.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.Order;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class OutboxService {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void publishOrderCreatedEvent(Order order) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "orderId", order.getId(),
                "userId", order.getUserId(),
                "totalPrice", order.getTotalPrice(),
                "status", order.getStatus().name()
            ));
            outboxRepository.save(new OutboxEvent("ORDER.CREATED", payload));
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문 이벤트 저장에 실패했습니다.");
        }
    }
}
