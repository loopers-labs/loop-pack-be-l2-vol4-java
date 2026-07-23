package com.loopers.tddstudy.interfaces.api.ranking;

import com.loopers.tddstudy.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(String date, String period, String periodKey,
                                  int page, int size, List<Item> items) {
    }

    public record Item(long rank, Long productId, String name, int price, double score) {
        public static Item from(RankingInfo info) {
            return new Item(info.rank(), info.productId(), info.name(), info.price(), info.score());
        }
    }
}
