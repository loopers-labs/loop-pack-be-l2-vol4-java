package com.loopers.application.catalog.ranking;

import java.time.LocalDate;

public class RankingQuery {

    public record Search(
        LocalDate date,
        int page,
        int size,
        String userId
    ) {
        public Search {
            if (page < 0) {
                page = 0;
            }
            if (size <= 0) {
                size = 20;
            }
        }
    }
}
