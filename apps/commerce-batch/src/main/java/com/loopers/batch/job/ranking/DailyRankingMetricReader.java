package com.loopers.batch.job.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Component
public class DailyRankingMetricReader {

    private final JdbcTemplate jdbcTemplate;

    public List<DailyRankingMetric> read(LocalDate requestDate) {
        return jdbcTemplate.query("""
            SELECT product_id,
                   SUM(view_count) AS view_count,
                   SUM(like_count) AS like_count,
                   SUM(sales_count) AS sales_count
            FROM product_metric_hourly
            WHERE metric_date = ?
            GROUP BY product_id
            """,
            (resultSet, rowNumber) -> new DailyRankingMetric(
                resultSet.getLong("product_id"),
                resultSet.getLong("view_count"),
                resultSet.getLong("like_count"),
                resultSet.getLong("sales_count")
            ),
            requestDate
        );
    }
}
