package com.loopers.application.ranking;

import java.util.List;

public record RankingPageInfo(
    String date,
    int page,
    int size,
    long totalCount,
    List<RankingItemInfo> items
) {}
