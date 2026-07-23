package com.loopers.tddstudy.application.ranking;

public record RankingScoreEvent(
        Long productId,
        String eventType,    // PRODUCT_LIKED / PRODUCT_UNLIKED / ORDER_SALES
        long occurredAt      // epoch millis — 어느 날짜 랭킹판에 넣을지 결정
) {
}
