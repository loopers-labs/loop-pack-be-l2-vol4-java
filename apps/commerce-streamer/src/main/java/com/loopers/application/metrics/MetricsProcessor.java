package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

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
        LocalDate date = RankingKeys.dateOf(msg.occurredAt()); // 랭킹과 동일한 일자 양자화 기준
        if ("ProductViewed".equals(msg.type())) {
            metricsRepository.addView(msg.productId(), date); // 누적 — 중복은 event_handled 가 차단
        } else {
            metricsRepository.applyLike(msg.productId(), date, msg.likeCount(), msg.version(), likeDelta(msg.type()));
        }
        record(msg.eventId());
    }

    @Transactional
    public void handleOrder(OrderEventMessage msg) {
        if (eventHandledRepository.existsByEventId(msg.eventId())) {
            return;
        }
        LocalDate date = RankingKeys.dateOf(msg.occurredAt());
        msg.lines().forEach(l -> metricsRepository.addSales(l.productId(), date, l.quantity()));
        record(msg.eventId());
    }

    /** 좋아요 순증감 — 취소는 음수. 모르는 타입은 스냅샷만 반영하고 증감은 0. */
    private static int likeDelta(String type) {
        return switch (type) {
            case "LikeAdded" -> 1;
            case "LikeRemoved" -> -1;
            default -> 0;
        };
    }

    private void record(String eventId) {
        try {
            eventHandledRepository.save(new EventHandled(eventId));
        } catch (DataIntegrityViolationException e) {
            // 경쟁 중복: 이미 다른 처리에서 기록됨 — 멱등 유지
        }
    }
}
