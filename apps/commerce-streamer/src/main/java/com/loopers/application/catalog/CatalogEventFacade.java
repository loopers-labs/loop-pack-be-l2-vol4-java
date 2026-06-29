package com.loopers.application.catalog;

import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.CatalogEventType;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CatalogEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다 (batch + manual ack 함정 대비 — 항목별 멱등이 필수).
     * 처음 보는 이벤트면 표시(markIfFirst)와 집계 반영을 같은 TX 로 묶어 원자성을 보장한다.
     */
    @Transactional
    public void handle(List<CatalogEventMessage> messages) {
        for (CatalogEventMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                applyMetric(message);
            }
        }
    }

    private void applyMetric(CatalogEventMessage message) {
        CatalogEventType.from(message.eventType())
            .ifPresent(type -> productMetricsRepository.applyLikeDelta(message.aggregateId(), type.likeDelta()));
    }
}
