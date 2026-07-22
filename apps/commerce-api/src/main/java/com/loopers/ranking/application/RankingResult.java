package com.loopers.ranking.application;

import java.util.List;

/**
 * 랭킹 조회 결과 묶음. degraded=true 면 Redis 장애 폴백(좋아요순)이라 score 가 null 이다.
 */
public class RankingResult {

    public record Page(List<Item> items, long total, int page, int size, boolean degraded) {
    }

    public record Item(long rank, Double score, long productId, String name, Long brandId, long price) {
    }
}
