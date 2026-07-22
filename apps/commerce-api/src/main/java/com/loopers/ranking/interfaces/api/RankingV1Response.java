package com.loopers.ranking.interfaces.api;

import com.loopers.ranking.application.RankingResult;

import java.util.List;

/**
 * 랭킹 조회 응답. degraded=true 면 Redis 장애 폴백(좋아요순)이라 score 가 빠진다(null 은 직렬화에서 생략).
 */
public class RankingV1Response {

    public record Page(List<Item> items, long total, int page, int size, boolean degraded) {
        public static Page from(RankingResult.Page page) {
            return new Page(
                    page.items().stream().map(Item::from).toList(),
                    page.total(), page.page(), page.size(), page.degraded());
        }
    }

    public record Item(long rank, Double score, long productId, String name, Long brandId, long price) {
        public static Item from(RankingResult.Item item) {
            return new Item(item.rank(), item.score(), item.productId(), item.name(), item.brandId(), item.price());
        }
    }
}
