package com.loopers.application.ranking;

import java.util.List;

public record RankingPageInfo(List<RankingItemInfo> items, long totalElements) {
}
