package com.loopers.application.catalog.ranking;

import com.loopers.domain.catalog.ranking.RankingPeriod;

import java.time.LocalDate;

public class RankingQuery {

    public record Search(
        LocalDate date,
        RankingPeriod period,
        int page,
        int size,
        String userId
    ) {
        public Search(LocalDate date, int page, int size, String userId) {
            this(date, RankingPeriod.DAILY, page, size, userId);
        }

        public Search {
            if (period == null) {
                period = RankingPeriod.DAILY;
            }
            if (page < 0) {
                page = 0;
            }
            if (size <= 0) {
                size = 20;
            }
        }
    }
}
