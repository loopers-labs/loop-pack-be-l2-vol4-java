package com.loopers.application.metrics;

import com.loopers.application.ranking.RankingContribution;
import com.loopers.confg.kafka.message.EventEnvelope;
import com.loopers.domain.metrics.EventHandled;
import com.loopers.infrastructure.metrics.EventHandledJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.infrastructure.metrics.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 배치 이벤트 집계 — 한 poll 배치를 productId 로 합산해 DB(product_metrics)와 랭킹 재료를 만든다.
 *
 * <h2>배치 멱등 (At Least Once 대응)</h2>
 * <ol>
 *   <li>배치 내 eventId 를 모아 {@code event_handled} 에서 이미 처리된 것을 <strong>한 번의 쿼리</strong>로 조회해 건너뛴다
 *       (건별 existsById 로 배치 효과를 깎지 않는다).</li>
 *   <li>메트릭 upsert(상품별 1회) + {@code event_handled} 벌크 INSERT 를 같은 트랜잭션으로 —
 *       재전달/중복 eventId 는 PK 위반으로 배치 전체가 롤백되고, 재전달 시 1번 가드가 걸러낸다.</li>
 * </ol>
 *
 * <p>랭킹 ZSET 반영은 이 트랜잭션 밖(커밋 후, {@code RankingRedisStore}, fail-open)에서 이뤄진다 —
 * DB 집계가 정합성의 원천이고 랭킹은 근사 뷰이므로 크로스-스토어 원자성을 요구하지 않는다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class MetricsBatchEventHandler {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;
    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    /**
     * 배치를 집계하고 DB 에 반영한 뒤, 랭킹 반영에 쓸 상품별 점수 재료를 돌려준다.
     *
     * @return productId → 이번 배치의 랭킹 기여(조회수/좋아요 증감/주문 로그점수). 신규 이벤트가 없으면 빈 맵.
     */
    @Transactional
    public Map<Long, RankingContribution> aggregateAndPersist(List<EventEnvelope> messages) {
        List<String> eventIds = messages.stream()
            .map(EventEnvelope::eventId)
            .filter(id -> id != null)
            .toList();
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Set<String> alreadyHandled = new HashSet<>(eventHandledJpaRepository.findHandledIds(eventIds));

        Map<Long, Accumulator> acc = new LinkedHashMap<>();
        Set<String> newlyHandled = new HashSet<>();   // 배치 내 중복 eventId 도 한 번만
        List<EventHandled> toRecord = new ArrayList<>();

        for (EventEnvelope envelope : messages) {
            String eventId = envelope.eventId();
            if (eventId == null) {
                log.warn("[Metrics] eventId 없는 이벤트 — 집계/멱등기록 없이 유실. eventType={}", envelope.eventType());
                continue;
            }
            if (alreadyHandled.contains(eventId) || !newlyHandled.add(eventId)) {
                continue;   // 이미 처리/배치 내 중복 — 건너뜀
            }
            toRecord.add(new EventHandled(eventId));
            try {
                aggregate(envelope, acc);
            } catch (Exception e) {
                // 메시지 하나가 깨져있다고(필드 누락/타입 불일치) 배치 전체를 롤백시키면 포이즌 필이 된다 —
                // ack 가 안 되어 Kafka 가 같은 배치를 영원히 재전달하며 해당 파티션 전체가 멈춘다.
                // 이 메시지만 집계 없이 건너뛰고(위에서 이미 event_handled 에 기록해 재시도도 안 함), 배치는 계속 진행한다.
                log.error("[Metrics] 이벤트 집계 실패 — 이 메시지만 건너뛰고 계속 진행. eventType={}, eventId={}",
                    envelope.eventType(), eventId, e);
            }
        }

        LocalDate metricDate = LocalDate.now();
        acc.forEach((productId, a) -> {
            productMetricsJpaRepository.upsertMetrics(
                productId, a.likeDelta, a.saleQty, a.viewCount, a.orderScore);
            productMetricsDailyJpaRepository.upsertDailyMetrics(
                metricDate, productId, a.likeDelta, a.saleQty, a.viewCount, a.orderScore);
        });
        eventHandledJpaRepository.saveAll(toRecord);

        Map<Long, RankingContribution> contributions = new LinkedHashMap<>();
        acc.forEach((productId, a) ->
            contributions.put(productId, new RankingContribution(a.viewCount, a.likeDelta, a.orderScore)));
        return contributions;
    }

    private void aggregate(EventEnvelope envelope, Map<Long, Accumulator> acc) {
        Map<String, Object> payload = envelope.payload();
        switch (envelope.eventType()) {
            case "LIKE_CHANGED" -> {
                Object type = payload.get("type");
                if ("LIKED".equals(type)) {
                    acc.computeIfAbsent(asLong(payload.get("productId")), k -> new Accumulator()).likeDelta += 1;
                } else if ("UNLIKED".equals(type)) {
                    acc.computeIfAbsent(asLong(payload.get("productId")), k -> new Accumulator()).likeDelta -= 1;
                } else {
                    log.warn("[Metrics] LIKE_CHANGED 알 수 없는 type — 집계 없이 소비. type={}, eventId={}",
                        type, envelope.eventId());
                }
            }
            case "PAYMENT_COMPLETED" -> {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) payload.get("items");
                if (items != null) {
                    for (Map<String, Object> item : items) {
                        long quantity = asLong(item.get("quantity"));
                        long price = asLong(item.get("price"));
                        Accumulator a = acc.computeIfAbsent(asLong(item.get("productId")), k -> new Accumulator());
                        a.saleQty += quantity;
                        // 주문별 log10(price*amount+1) 합 — 상품별로 price*amount 를 먼저 합치지 않는다.
                        a.orderScore += Math.log10((double) price * quantity + 1);
                    }
                }
            }
            case "PRODUCT_VIEWED" ->
                acc.computeIfAbsent(asLong(payload.get("productId")), k -> new Accumulator()).viewCount += 1;
            default ->
                log.warn("[Metrics] 알 수 없는 eventType — 집계 없이 소비. eventType={}, eventId={}",
                    envelope.eventType(), envelope.eventId());
        }
    }

    private static long asLong(Object value) {
        return ((Number) value).longValue();
    }

    /** productId 별 배치 누적기 — DB(like/sale/view)와 랭킹(orderScore) 재료를 함께 쌓는다. */
    private static final class Accumulator {
        long likeDelta;
        long saleQty;
        long viewCount;
        double orderScore;
    }
}
