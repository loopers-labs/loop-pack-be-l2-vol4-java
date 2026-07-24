package com.loopers.domain.ranking.batch;

import java.util.List;

/**
 * mv_product_rank_staging에 대한 포트. aggregateStep 종료 시 Top100 결과를 저장하고,
 * publishStep에서 검증을 위해 다시 읽어온다.
 */
public interface RankingStagingRepository {

    void deleteByPeriod(RankingBatchPeriodType periodType, String periodKey);

    void saveAll(RankingBatchPeriodType periodType, String periodKey, List<RankingStagingRankRow> rows);

    List<RankingStagingRankRow> findByPeriod(RankingBatchPeriodType periodType, String periodKey);
}
