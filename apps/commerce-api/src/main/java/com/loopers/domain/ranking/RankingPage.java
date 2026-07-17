package com.loopers.domain.ranking;

import java.util.List;

public record RankingPage(List<RankingEntry> entries, long totalCount) {}
