package com.loopers.application.ranking;

import com.loopers.application.metrics.CatalogEventMessage;
import com.loopers.application.metrics.OrderEventMessage;
import com.loopers.domain.ranking.RankingKeys;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 이벤트 메시지 → 점수 delta → 랭킹 반영. 멱등은 RankingRepository.applyOnce(SETNX+Lua)가 보장한다.
 * 메시지 계약은 metrics 컨슈머와 동일 토픽을 재소비하므로 application.metrics 의 record 를 재사용한다.
 */
@RequiredArgsConstructor
@Component
public class RankingProcessor {

    private final RankingScorePolicy scorePolicy;
    private final RankingRepository rankingRepository;

    public void handleCatalog(CatalogEventMessage msg) {
        double delta = scorePolicy.catalogDelta(msg.type());
        if (delta == 0.0) {
            return; // 랭킹 무관 타입
        }
        rankingRepository.applyOnce(msg.eventId(), RankingKeys.dateOf(msg.occurredAt()),
            Map.of(msg.productId(), delta));
    }

    public void handleOrder(OrderEventMessage msg) {
        Map<Long, Double> deltas = new LinkedHashMap<>();
        msg.lines().forEach(line ->
            deltas.merge(line.productId(), scorePolicy.orderDelta(line.quantity()), Double::sum));
        rankingRepository.applyOnce(msg.eventId(), RankingKeys.dateOf(msg.occurredAt()), deltas);
    }
}
