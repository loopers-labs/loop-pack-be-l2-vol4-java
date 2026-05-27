package com.loopers.application.event.relay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.dataplatform.DataPlatformClient;
import com.loopers.domain.event.dataplatform.DataPlatformResult;
import com.loopers.domain.event.order.OrderPaidEvent;
import com.loopers.domain.event.outbox.OrderEventOutbox;
import com.loopers.domain.event.outbox.OrderEventOutboxRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class EventRelayWorker {

    private static final int BATCH_SIZE = 100;

    private final OrderEventOutboxRepository orderEventOutboxRepository;
    private final DataPlatformClient dataPlatformClient;
    private final ObjectMapper objectMapper;
    private final EventRelayResultService eventRelayResultService;

    @Transactional
    public List<RelayResult> relayPendingEvents() {
        return orderEventOutboxRepository.findPendingEventsForUpdate(BATCH_SIZE)
            .stream()
            .map(this::relay)
            .toList();
    }

    private RelayResult relay(OrderEventOutbox outbox) {
        if (!outbox.isPending()) {
            return RelayResult.noop(outbox.getOrderId());
        }

        DataPlatformResult result = dataPlatformClient.sendOrderPaid(deserialize(outbox.getPayload()));
        if (result.succeeded()) {
            return eventRelayResultService.markSent(outbox);
        }

        return eventRelayResultService.recordFailure(outbox);
    }

    private OrderPaidEvent deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, OrderPaidEvent.class);
        } catch (JsonProcessingException e) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "주문 완료 이벤트 payload 해석에 실패했습니다.");
        }
    }

    public record RelayResult(Status status, Long orderId) {
        public enum Status {
            SENT,
            RETRY,
            FAILED,
            NOOP
        }

        public static RelayResult sent(Long orderId) {
            return new RelayResult(Status.SENT, orderId);
        }

        public static RelayResult retry(Long orderId) {
            return new RelayResult(Status.RETRY, orderId);
        }

        public static RelayResult failed(Long orderId) {
            return new RelayResult(Status.FAILED, orderId);
        }

        private static RelayResult noop(Long orderId) {
            return new RelayResult(Status.NOOP, orderId);
        }
    }
}
