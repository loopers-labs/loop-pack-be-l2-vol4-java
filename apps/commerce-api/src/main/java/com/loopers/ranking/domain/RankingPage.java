package com.loopers.ranking.domain;

import java.util.List;

public record RankingPage(List<RankingEntry> entries, long totalCount) {

    public RankingPage {
        entries = List.copyOf(entries);
    }

    public static RankingPage empty() {
        return new RankingPage(List.of(), 0L);
    }
}
