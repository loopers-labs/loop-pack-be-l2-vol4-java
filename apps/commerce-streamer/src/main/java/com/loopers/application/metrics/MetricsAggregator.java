package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyUpdater;
import com.loopers.infrastructure.metrics.ProductMetricsDailyUpdater.DailyDelta;
import com.loopers.infrastructure.metrics.ProductMetricsDailyUpdater.DailyKey;
import com.loopers.application.ranking.RankingScorePolicy;
import com.loopers.infrastructure.metrics.ProductMetricsUpdater;
import com.loopers.infrastructure.ranking.RankingKey;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * catalog-events / order-events 배치를 받아 product_metrics 측정값에 반영하는 집계 로직.
 *
 * <p><b>멱등 + coalescing</b>: 배치 내 같은 productId 델타를 메모리에서 합산해 상품당 UPDATE 1회로 줄이고
 * (hot row 완화, 가산=교환법칙이라 순서 무관), event_handled 로 이미 처리한 eventId 는 건너뛴다.
 * 집계 반영과 처리 마킹을 <b>한 트랜잭션</b>으로 묶어 재전달 시 이중 반영을 막는다(컨슈머가 커밋 후 ack).
 *
 * <p><b>두 테이블을 함께 갱신한다</b>(week10):
 * <ul>
 *   <li>{@code product_metrics} — 상품당 1행, <b>누적</b> 총합. 상품 리스팅 read model.</li>
 *   <li>{@code product_metrics_daily} — (일자, 상품) 1행, <b>그날의 델타</b>. 주간/월간 랭킹 집계의 원천.</li>
 * </ul>
 * 같은 트랜잭션에서 쓰므로 둘이 어긋나지 않고, {@code event_handled} 멱등도 한 번만 검사하면 된다.
 * 누적을 날짜별로 쪼개 저장하지 않고 테이블을 나눈 이유는 {@code ProductMetricsDailyEntity} javadoc 참고.
 *
 * <p>eventType 별 해석:
 * <ul>
 *   <li>{@code LIKE_CHANGED}  payload {productId, delta} → like_count += delta / like_delta += delta</li>
 *   <li>{@code PRODUCT_VIEWED} payload {productId}        → view_count += 1</li>
 *   <li>{@code ORDER_PAID}    payload {items:[{productId, quantity, unitPrice}]}
 *       → sales_count += quantity, sales_amount += unitPrice×quantity</li>
 * </ul>
 * 알 수 없는 eventType 은 로그만 남기고 스킵하되 handled 로 마킹한다(무한 재처리 방지 — DLT-lite).
 */
@Component
@RequiredArgsConstructor
public class MetricsAggregator {

    public static final String CONSUMER_GROUP = "metrics-aggregator";

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregator.class);

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsUpdater productMetricsUpdater;
    private final ProductMetricsDailyUpdater productMetricsDailyUpdater;

    @Transactional
    public void apply(List<EventEnvelope> envelopes) {
        // 배치 내 중복 eventId 제거(먼저 온 것 유지) — 멱등 1차 방어
        Map<Long, EventEnvelope> byEventId = new LinkedHashMap<>();
        for (EventEnvelope e : envelopes) {
            if (e.eventId() != null) {
                byEventId.putIfAbsent(e.eventId(), e);
            }
        }
        if (byEventId.isEmpty()) {
            return;
        }

        // 이미 처리한 eventId 제거 — 멱등 2차 방어(재전달 대비)
        Set<Long> handled = eventHandledRepository.findHandled(CONSUMER_GROUP, byEventId.keySet());
        List<Long> freshIds = byEventId.keySet().stream()
                .filter(id -> !handled.contains(id))
                .toList();
        if (freshIds.isEmpty()) {
            return;
        }

        Map<Long, Long> likeDeltas = new HashMap<>();
        Map<Long, Long> viewDeltas = new HashMap<>();
        Map<Long, Long> salesDeltas = new HashMap<>();
        Map<DailyKey, DailyDelta> dailyDeltas = new HashMap<>();

        for (Long id : freshIds) {
            EventEnvelope e = byEventId.get(id);
            JsonNode payload = e.payload();
            // 일자는 이벤트 발생시각(KST) 기준 — 랭킹 ZSET 과 같은 규약을 써야 두 집계를 대조할 수 있다.
            LocalDate metricDate = RankingKey.dateOf(e.occurredAt());
            switch (e.eventType()) {
                case "LIKE_CHANGED" -> {
                    long productId = payload.get("productId").asLong();
                    long delta = payload.get("delta").asLong();
                    likeDeltas.merge(productId, delta, Long::sum);
                    addDaily(dailyDeltas, metricDate, productId, DailyDelta.ofLike(delta));
                }
                case "PRODUCT_VIEWED" -> {
                    long productId = payload.get("productId").asLong();
                    viewDeltas.merge(productId, 1L, Long::sum);
                    addDaily(dailyDeltas, metricDate, productId, DailyDelta.ofView(1L));
                }
                case "ORDER_PAID" -> {
                    for (JsonNode item : payload.get("items")) {
                        long productId = item.get("productId").asLong();
                        long quantity = item.get("quantity").asLong();
                        // unitPrice 는 week9 에 추가된 필드 — 구(舊) 이벤트 호환을 위해 없으면 0(매출 0으로 적재)
                        long unitPrice = item.hasNonNull("unitPrice") ? item.get("unitPrice").asLong() : 0L;
                        salesDeltas.merge(productId, quantity, Long::sum);
                        // 주문 스코어는 건별로 계산해 더한다 — 기간 집계에서 log 를 다시 씌우지 않기 위해서다.
                        addDaily(dailyDeltas, metricDate, productId, DailyDelta.ofOrder(
                                quantity, unitPrice * quantity, RankingScorePolicy.orderScore(unitPrice, quantity)));
                    }
                }
                default -> log.warn("알 수 없는 eventType 스킵(handled 마킹): eventId={}, type={}", id, e.eventType());
            }
        }

        productMetricsUpdater.applyLikeDeltas(likeDeltas);
        productMetricsUpdater.applyViewDeltas(viewDeltas);
        productMetricsUpdater.applySalesDeltas(salesDeltas);
        productMetricsDailyUpdater.applyDailyDeltas(dailyDeltas);
        eventHandledRepository.markHandled(CONSUMER_GROUP, freshIds);

        log.debug("product_metrics 집계: fresh={}, like={}, view={}, sales={}, daily={}",
                freshIds.size(), likeDeltas.size(), viewDeltas.size(), salesDeltas.size(), dailyDeltas.size());
    }

    /** {@code (일자, 상품)} 버킷에 델타를 누적한다. 같은 배치에 같은 상품의 여러 이벤트가 와도 UPSERT 1회로 합쳐진다. */
    private void addDaily(Map<DailyKey, DailyDelta> dailyDeltas, LocalDate date, long productId, DailyDelta delta) {
        dailyDeltas.merge(new DailyKey(date, productId), delta, DailyDelta::plus);
    }
}
