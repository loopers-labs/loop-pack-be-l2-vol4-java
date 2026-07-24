package com.loopers.application.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 이벤트 1건을 멱등하게 집계에 반영한다.
 * 멱등 장부(event_handled) 기록과 집계 upsert 를 한 트랜잭션으로 묶는다 —
 * "장부엔 적었는데 집계 전에 죽음"(누락) / "집계했는데 장부 기록 전에 죽음"(중복) 의 틈을 없애기 위해.
 * 랭킹 ZSET 갱신(Redis)은 이 트랜잭션 범위 밖의 부수 효과다 — 롤백돼도 함께 되돌아가지 않지만,
 * 랭킹은 SoT 가 아닌 파생 뷰라 감수 가능한 오차로 판단했다 (week9 qna 참고).
 */
@RequiredArgsConstructor
@Component
public class MetricsAggregationService {

    private final EventHandledRepository eventHandledRepository;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingRepository rankingRepository;

    @Transactional
    public void aggregate(String eventId, String eventType, JsonNode payload) {
        if (eventHandledRepository.alreadyHandled(eventId)) {
            return; // 중복 배달(At Least Once 의 대가) — 이미 반영된 이벤트는 통째로 skip
        }
        eventHandledRepository.markHandled(eventId);

        LocalDate today = LocalDate.now(); // 이벤트 발생 시각이 아닌 컨슈머 처리 시각 기준 (week9 qna Q7)
        switch (eventType) {
            case "ProductLikedEvent" -> {
                Long productId = payload.get("productId").asLong();
                productMetricsRepository.increaseLikeCount(productId, today);
                rankingRepository.incrementScore(today, productId, RankingScorePolicy.likeScore());
            }
            case "ProductUnlikedEvent" -> {
                Long productId = payload.get("productId").asLong();
                productMetricsRepository.decreaseLikeCount(productId, today);
                rankingRepository.incrementScore(today, productId, RankingScorePolicy.unlikeScore());
            }
            case "ProductViewedEvent" -> {
                Long productId = payload.get("productId").asLong();
                productMetricsRepository.increaseViewCount(productId, today);
                rankingRepository.incrementScore(today, productId, RankingScorePolicy.viewScore());
            }
            // 판매량은 주문 생성 기준으로 집계한다 (결제 실패 시 과대 집계 가능 — 파생 지표라 감수, 정밀화는 추후 과제).
            case "OrderCreatedEvent" -> payload.get("items").forEach(item -> {
                Long productId = item.get("productId").asLong();
                int quantity = item.get("quantity").asInt();
                double unitPrice = item.get("unitPrice").get("amount").asDouble();
                // 정규화된 주문 점수는 한 번만 계산해 일별 집계(order_score)와 ZSET 에 같은 값을 넣는다 — 일간/주간 점수 기준을 일치시키기 위함.
                double orderScore = RankingScorePolicy.orderScore(unitPrice, quantity);
                productMetricsRepository.increaseSaleCount(productId, quantity, orderScore, today);
                rankingRepository.incrementScore(today, productId, orderScore);
            });
            default -> {
                // 집계 대상이 아닌 이벤트(결제 확정 등)도 장부에는 남긴다 — 재배달 시 다시 파싱하지 않도록.
            }
        }
    }
}
