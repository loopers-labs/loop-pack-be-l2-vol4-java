package com.loopers.ranking.application;

import com.loopers.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.Optional;

public interface PublishedRankingQuery {

    Optional<PublishedRanking> findLatestCompleted(RankingPeriod period, LocalDate date);
}
