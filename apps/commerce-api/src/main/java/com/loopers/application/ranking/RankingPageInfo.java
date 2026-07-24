package com.loopers.application.ranking;

import java.util.List;

/**
 * 랭킹 한 페이지.
 * {@code periodKey} 는 기준일이 어느 구간으로 해석됐는지 그대로 드러낸다
 * (일간 20260722 / 주간 2026-W30 / 월간 2026-07).
 */
public record RankingPageInfo(
    String period,
    String periodKey,
    int page,
    int size,
    long totalCount,
    List<RankingItemInfo> items
) {}
