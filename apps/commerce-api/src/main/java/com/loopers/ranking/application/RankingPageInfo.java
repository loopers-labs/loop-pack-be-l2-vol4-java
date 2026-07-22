package com.loopers.ranking.application;

import java.util.List;

public record RankingPageInfo(
        List<RankingItemInfo> items, int page, int size, long totalCount, int totalPages) {}
