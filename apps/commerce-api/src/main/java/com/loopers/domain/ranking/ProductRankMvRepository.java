package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

// 배치가 적재한 주간/월간 랭킹 MV 를 조회하는 포트. 구현은 infrastructure 의 JPA 어댑터가 담당한다.
public interface ProductRankMvRepository {

    List<RankingEntry> findRankings(RankingPeriod period, LocalDate aggregateDate, int page, int size);

    long count(RankingPeriod period, LocalDate aggregateDate);
}
