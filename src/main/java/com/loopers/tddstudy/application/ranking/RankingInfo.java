package com.loopers.tddstudy.application.ranking;

public record RankingInfo(
        long rank,        // 1-based, 페이지 이어짐
        Long productId,
        String name,
        int price,
        double score
) {
}
