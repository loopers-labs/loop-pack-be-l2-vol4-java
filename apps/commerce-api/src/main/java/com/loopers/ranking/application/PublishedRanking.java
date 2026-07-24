package com.loopers.ranking.application;

import java.util.List;

public record PublishedRanking(
    List<RankingPosition> positions
) {

    public PublishedRanking {
        positions = List.copyOf(positions);
    }
}
