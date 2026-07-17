package com.loopers.ranking.application;

import com.loopers.ranking.domain.RankingSignal;

import java.time.LocalDate;

/**
 * 반영 대상 랭킹 이벤트. date 는 컨슈머가 occurredAt(KST)으로 계산해 채운다.
 * delta 는 조회=+1, 좋아요=±1, 주문=수량.
 */
public record RankingEvent(LocalDate date, long productId, RankingSignal signal, long delta) {
}
