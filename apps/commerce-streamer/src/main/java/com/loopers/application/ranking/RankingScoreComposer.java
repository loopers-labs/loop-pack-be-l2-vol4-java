package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSignal;
import com.loopers.support.config.RankingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Score 구간 — raw 신호 × 가중치(properties) → display 보드 합성의 단일 정의.
 * 주기 합성 스케줄러와 23:50 carry-over(익일 시드 직후 즉시 익일 보드를 합성)가 공유한다.
 * 가중치 매핑이 여기 한 곳에만 존재한다.
 */
@Component
@RequiredArgsConstructor
public class RankingScoreComposer {

    private final RankingRepository rankingRepository;
    private final RankingProperties rankingProperties;

    public void compose(LocalDate date) {
        rankingRepository.compose(date, weights());
    }

    private Map<RankingSignal, Double> weights() {
        RankingProperties.Weight weight = rankingProperties.weight();
        Map<RankingSignal, Double> weights = new EnumMap<>(RankingSignal.class);
        weights.put(RankingSignal.VIEW, weight.view());
        weights.put(RankingSignal.LIKE, weight.like());
        weights.put(RankingSignal.ORDER_COUNT, weight.orderCount());
        weights.put(RankingSignal.ORDER_QTY, weight.orderQty());
        return weights;
    }
}
