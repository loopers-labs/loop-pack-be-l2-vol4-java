package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.RankingPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class ProductRankMvStore {

    private final JdbcTemplate jdbcTemplate;

    public List<Long> findProductIds(RankingPeriod period, LocalDate date, long start, long end) {
        if (start > end) {
            return List.of();
        }
        String tableName = switch (period) {
            case WEEKLY -> "mv_product_rank_weekly";
            case MONTHLY -> "mv_product_rank_monthly";
            case DAILY -> throw new IllegalArgumentException("daily ranking does not use materialized view");
        };
        int limit = (int) (end - start + 1);
        return jdbcTemplate.queryForList(
            "SELECT product_id FROM " + tableName + " "
                + "WHERE period_start_date = ? ORDER BY rank_no ASC LIMIT ? OFFSET ?",
            Long.class,
            period.startDate(date),
            limit,
            start);
    }
}
