package com.loopers.application.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.application.ranking.RankingScoreReflector;
import com.loopers.domain.idempotency.EventHandledRepository;
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
public class OrderEventFacade {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingScorePolicy rankingScorePolicy;
    private final RankingScoreReflector rankingScoreReflector;

    /**
     * 한 배치를 한 트랜잭션으로 처리한다. 처음 보는 이벤트면 표시(markIfFirst)와 판매량 반영을 같은 TX 로 묶는다.
     * 판매량은 수량만큼 누적(delta)되는 commutative 값이라 순서와 무관하며, 멱등이 재전송/배치 재처리 중복을 막는다.
     * 랭킹 점수는 커밋 후에만 반영하도록 델타만 모아 reflector 에 넘긴다(결정 #4).
     */
    @Transactional
    public void handle(List<OrderEventMessage> messages) {
        LocalDate today = LocalDate.now();
        Map<Long, Double> scoreDeltas = new HashMap<>();
        for (OrderEventMessage message : messages) {
            if (eventHandledRepository.markIfFirst(message.eventId())) {
                applySales(message, scoreDeltas, today);
            }
        }
        rankingScoreReflector.reflectAfterCommit(RankingKey.of(today), scoreDeltas);
    }

    private void applySales(OrderEventMessage message, Map<Long, Double> scoreDeltas, LocalDate today) {
        JsonNode lines = message.data().get("lines");
        if (lines == null) {
            return;
        }
        for (JsonNode line : lines) {
            long productId = line.get("productId").asLong();
            long quantity = line.get("quantity").asLong();
            productMetricsRepository.applySalesDelta(productId, today, quantity);
            long lineAmount = line.path("lineAmount").asLong(0L);
            scoreDeltas.merge(productId, rankingScorePolicy.scoreFor(RankingSignal.ORDER, lineAmount), Double::sum);
        }
    }
}
