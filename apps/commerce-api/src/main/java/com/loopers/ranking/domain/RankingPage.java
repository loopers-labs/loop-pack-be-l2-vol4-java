package com.loopers.ranking.domain;

import java.util.List;

public record RankingPage(List<RankingEntry> entries, long totalCount) {}
