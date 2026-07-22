package com.loopers.application.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.ranking.RankingScoreReflector;
import com.loopers.domain.idempotency.EventHandledRepository;
import com.loopers.domain.metrics.CatalogEventType;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingKey;
import com.loopers.domain.ranking.RankingScorePolicy;
import com.loopers.domain.ranking.RankingSignal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CatalogEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingScoreReflector rankingScoreReflector;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다 (batch + manual ack 함정 대비 — 항목별 멱등이 필수).
     * 처음 보는 이벤트면 표시(markIfFirst)와 집계 반영을 같은 TX 로 묶어 원자성을 보장한다.
     * 랭킹 점수는 커밋 후에만 반영하도록 델타만 모아 reflector 에 넘긴다(결정 #4).
     */
    @Transactional
    public void handle(List<CatalogEventMessage> messages) {
        LocalDate today = LocalDate.now();
        Map<Long, Double> scoreDeltas = new HashMap<>();
        for (CatalogEventMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                applyMetric(message, today);
                accumulateScore(message, scoreDeltas);
            }
        }
        rankingScoreReflector.reflectAfterCommit(RankingKey.of(today), scoreDeltas);
    }

    private void accumulateScore(CatalogEventMessage message, Map<Long, Double> scoreDeltas) {
        RankingSignal.fromCatalogEventType(message.eventType())
            .ifPresent(signal -> scoreDeltas.merge(
                message.aggregateId(), rankingScorePolicy.scoreFor(signal, 0), Double::sum));
    }

    private static final String STOCK_CHANGED = "STOCK_CHANGED";
    private static final String VIEWED = "VIEWED";

    private void applyMetric(CatalogEventMessage message, LocalDate today) {
        if (STOCK_CHANGED.equals(message.eventType())) {
            applyStockState(message);
            return;
        }
        if (VIEWED.equals(message.eventType())) {
            productMetricsRepository.applyViewDelta(message.aggregateId(), today, 1);
            return;
        }
        // like/unlike 처럼 '누적(delta)'되는 이벤트: 순서와 무관(commutative)해 멱등만으로 정확.
        CatalogEventType.from(message.eventType())
            .ifPresent(type -> productMetricsRepository.applyLikeDelta(message.aggregateId(), today, type.likeDelta()));
    }

    // 재고는 '절대 상태'라 delta 가 아니라 최신 값으로 덮어쓴다 — version 으로 최신성을 가드한다.
    private void applyStockState(CatalogEventMessage message) {
        JsonNode data = message.data();
        long quantity = data.get("quantity").asLong();
        long version = data.get("version").asLong();
        productMetricsRepository.applyStockState(message.aggregateId(), quantity, version);
    }
}
