package com.loopers.ranking.domain;

import java.time.LocalDate;

/** 배치가 생성한 주간·월간 조회 전용 스냅샷 저장소. */
public interface MaterializedRankingRepository {

    RankingPage findPage(
            RankingPeriod period, LocalDate aggregationDate, int page, int size);
}
