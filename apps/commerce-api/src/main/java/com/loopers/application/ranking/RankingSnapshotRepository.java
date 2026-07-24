package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingPeriod;

import java.util.List;

public interface RankingSnapshotRepository {

    List<RankingEntry> findRankings(RankingPeriod period, String startDate, String endDate, int page, int size);

    long count(RankingPeriod period, String startDate, String endDate);
}
