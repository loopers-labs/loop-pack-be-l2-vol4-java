package com.loopers.application.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.event.CouponIssueRequestedEvent;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@RequiredArgsConstructor
@Component
public class CouponIssueOutboxListener {

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final CatalogEventTopicProperties topicProperties;
    private final MeterRegistry meterRegistry;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(CouponIssueRequestedEvent event) {
        try {
            outboxEventRepository.save(new OutboxEvent(
                event.eventId(),
                topicProperties.couponIssueRequests(),
                String.valueOf(event.couponId()),
                event.eventType(),
                objectMapper.writeValueAsString(event.toEnvelope())
            ));
            meterRegistry.counter("outbox_event_saved_total", "eventType", event.eventType()).increment();
        } catch (JsonProcessingException exception) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, "쿠폰 발급 요청 이벤트 직렬화에 실패했습니다.");
        }
    }
}
