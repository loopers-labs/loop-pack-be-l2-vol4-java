package com.loopers.application.ranking;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.metrics.EventEnvelope;
import com.loopers.infrastructure.metrics.EventHandledRepository;
import com.loopers.infrastructure.ranking.RankingKey;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
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
 * catalog-events / order-events 배치를 받아 일간 랭킹 ZSET({@code ranking:all:{yyyyMMdd}})에 가중 점수를 누적한다.
 * product_metrics DB 집계({@code MetricsAggregator})와 <b>별개 컨슈머 그룹</b>으로 같은 토픽을 소비해,
 * 랭킹 파이프라인(Redis)과 측정값 파이프라인(DB)이 서로의 장애/오프셋에 독립적이다.
 *
 * <p><b>배치 내 coalescing</b>: {@code (일간 키, productId)} 단위로 점수 델타를 메모리에서 합산해 상품당 ZINCRBY 를
 * 1회로 줄인다(hot key 완화, 가산=교환법칙이라 순서 무관). 일자는 이벤트 발생시각 기준이라 자정 근처 이벤트도
 * 올바른 날짜 버킷에 들어간다.
 *
 * <p><b>멱등</b>: {@code ZINCRBY} 는 그 자체로 멱등이 아니라 같은 이벤트를 두 번 받으면 이중 가산된다. 중복은
 * 드문 일이 아니다 — 하이브리드 Outbox(즉시 발행 + 릴레이 폴링)가 같은 행을 두 번 보낼 수 있고, 이는 실측으로
 * 확인됐다(E2E 13건 중 1건). 그래서 {@code metrics-aggregator} 와 동일하게 event_handled 로 그룹별 1회 처리를
 * 보장한다: 배치 내 eventId 중복 제거(1차) → 이미 처리한 eventId 필터(2차) → Redis 반영 → 같은 트랜잭션에서 마킹.
 *
 * <p><b>남는 창(window)</b>: Redis 와 DB 는 한 트랜잭션으로 묶을 수 없다. Redis 반영 후 마킹 커밋 전에 죽으면
 * 재전달 시 그 배치만 이중 가산된다(유실은 없음 — at-least-once). 이 잔여 오차는 랭킹이 근사값이고 2일 TTL 로
 * 리셋되므로 수용한다. 마킹을 먼저 커밋하는 반대 순서는 이중 가산 대신 <b>유실</b>이 되어 더 나쁘다.
 *
 * <p>eventType 별 해석:
 * <ul>
 *   <li>{@code PRODUCT_VIEWED} payload {productId}                    → +조회 스코어</li>
 *   <li>{@code LIKE_CHANGED}   payload {productId, delta}             → ±좋아요 스코어</li>
 *   <li>{@code ORDER_PAID}     payload {items:[{productId, quantity, unitPrice}]} → +주문(매출) 스코어</li>
 * </ul>
 * 그 외 eventType 은 랭킹과 무관하므로 스킵하되 handled 로 마킹한다(무한 재조회 방지 — metrics 와 동일).
 */
@Component
@RequiredArgsConstructor
public class RankingAggregator {

    public static final String CONSUMER_GROUP = "ranking-aggregator";

    private static final Logger log = LoggerFactory.getLogger(RankingAggregator.class);

    private final RankingRedisRepository rankingRedisRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void apply(List<EventEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return;
        }

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

        // 이미 처리한 eventId 제거 — 멱등 2차 방어(재전달·중복 발행 대비)
        Set<Long> handled = eventHandledRepository.findHandled(CONSUMER_GROUP, byEventId.keySet());
        List<Long> freshIds = byEventId.keySet().stream()
                .filter(id -> !handled.contains(id))
                .toList();
        if (freshIds.isEmpty()) {
            return;
        }

        // key(일간) -> (productId(member) -> scoreDelta) 배치 내 합산
        Map<String, Map<String, Double>> deltasByKey = new HashMap<>();

        for (Long id : freshIds) {
            EventEnvelope e = byEventId.get(id);
            if (e.eventType() == null || e.payload() == null) {
                continue;
            }
            LocalDate date = RankingKey.dateOf(e.occurredAt());
            String key = RankingKey.daily(date);
            JsonNode payload = e.payload();

            switch (e.eventType()) {
                case "PRODUCT_VIEWED" -> add(deltasByKey, key,
                        payload.get("productId").asLong(), RankingScorePolicy.viewScore());
                case "LIKE_CHANGED" -> add(deltasByKey, key,
                        payload.get("productId").asLong(), RankingScorePolicy.likeScore(payload.get("delta").asLong()));
                case "ORDER_PAID" -> {
                    for (JsonNode item : payload.get("items")) {
                        long productId = item.get("productId").asLong();
                        long quantity = item.get("quantity").asLong();
                        // unitPrice 는 week9 에 추가된 필드 — 구(舊) 이벤트 호환을 위해 없으면 0(가산 없음)
                        long unitPrice = item.hasNonNull("unitPrice") ? item.get("unitPrice").asLong() : 0L;
                        add(deltasByKey, key, productId, RankingScorePolicy.orderScore(unitPrice, quantity));
                    }
                }
                default -> {
                    // 랭킹과 무관한 이벤트 스킵 — 아래에서 handled 로 마킹되어 다시 조회되지 않는다
                }
            }
        }

        rankingRedisRepository.incrementAll(deltasByKey);
        eventHandledRepository.markHandled(CONSUMER_GROUP, freshIds);

        log.debug("랭킹 집계: envelopes={}, fresh={}, keys={}", envelopes.size(), freshIds.size(), deltasByKey.size());
    }

    private void add(Map<String, Map<String, Double>> deltasByKey, String key, long productId, double score) {
        if (score == 0.0) {
            return;
        }
        deltasByKey.computeIfAbsent(key, k -> new HashMap<>())
                .merge(String.valueOf(productId), score, Double::sum);
    }
}
