package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 주간 랭킹 MV 조회. */
@Component
public class WeeklyRankingRepository extends AbstractMvRankingRepository {

    public WeeklyRankingRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
    }

    @Override
    public RankingPeriod period() {
        return RankingPeriod.WEEKLY;
    }

    @Override
    protected String tableName() {
        return "mv_product_rank_weekly";
    }
}
