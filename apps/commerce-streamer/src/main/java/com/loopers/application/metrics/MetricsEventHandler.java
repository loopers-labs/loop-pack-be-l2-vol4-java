package com.loopers.application.metrics;

import com.loopers.confg.kafka.message.EventEnvelope;
import com.loopers.domain.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 이벤트 1건 처리 — 멱등 가드 + 메트릭 집계 + 처리 기록을 단일 트랜잭션으로.
 *
 * <h2>멱등 처리 (At Least Once 대응)</h2>
 * <ol>
 *   <li>{@code event_handled} 에 eventId 존재 → 이미 처리된 이벤트, 건너뜀.</li>
 *   <li>메트릭 upsert + {@code event_handled} INSERT 를 같은 트랜잭션으로 —
 *       동시에 같은 eventId 가 들어와도 PK 위반으로 진 쪽 트랜잭션 전체가 롤백되어
 *       집계는 정확히 한 번만 반영된다(패자는 재전달 시 1번 가드에서 걸러짐).</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MetricsEventHandler {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public void handle(EventEnvelope envelope) {
        if (envelope.eventId() == null || eventHandledJpaRepository.existsById(envelope.eventId())) {
            return;   // 이미 처리됨 — 멱등 스킵
        }

        Map<String, Object> payload = envelope.payload();
        switch (envelope.eventType()) {
            case "LIKE_CHANGED" -> {
                long delta = "LIKED".equals(payload.get("type")) ? 1L : -1L;
                productMetricsJpaRepository.upsertLikeCount(asLong(payload.get("productId")), delta);
            }
            case "PAYMENT_COMPLETED" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        productMetricsJpaRepository.upsertSaleCount(asLong(item.get("productId")), asLong(item.get("quantity")));
                    }
                }
            }
            case "PRODUCT_VIEWED" -> productMetricsJpaRepository.upsertViewCount(asLong(payload.get("productId")));
            default ->
                // 모르는 타입 — 재전달 루프에 빠지지 않도록 기록만 하고 소비 처리한다
                log.warn("[Metrics] 알 수 없는 eventType — 집계 없이 소비. eventType={}, eventId={}",
                    envelope.eventType(), envelope.eventId());
        }

        eventHandledJpaRepository.save(new EventHandled(envelope.eventId()));
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }
}
