package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MetricsProcessor {

    private final ProductMetricsRepository metricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handleCatalog(CatalogEventMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.eventId())) {
            return; // 멱등
        }
        metricsRepository.applyLike(msg.productId(), msg.likeCount(), msg.version());
        record(msg.eventId());
    }

    @Transactional
    public void handleOrder(OrderEventMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.eventId())) {
            return;
        }
        msg.lines().forEach(l -> metricsRepository.addSales(l.productId(), l.quantity()));
        record(msg.eventId());
    }

    private void record(String eventId) {
        try {
            eventHandledRepository.save(new EventHandled(eventId));
        } catch (DataIntegrityViolationException e) {
            // 경쟁 중복: 이미 다른 처리에서 기록됨 — 멱등 유지
        }
    }
}
