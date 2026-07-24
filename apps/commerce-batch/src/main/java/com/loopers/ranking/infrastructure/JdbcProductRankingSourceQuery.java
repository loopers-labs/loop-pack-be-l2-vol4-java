package com.loopers.ranking.infrastructure;

import com.loopers.ranking.application.ProductRankingSourceQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class JdbcProductRankingSourceQuery implements ProductRankingSourceQuery {

    private static final String COUNT_PRODUCTS_SQL = """
        select count(distinct product_id)
        from product_metrics
        where metric_date between ? and ?
        """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public long countProductsWithMetrics(
        LocalDate periodStartInclusive,
        LocalDate aggregationEndDateInclusive
    ) {
        Long count = jdbcTemplate.queryForObject(
            COUNT_PRODUCTS_SQL,
            Long.class,
            periodStartInclusive,
            aggregationEndDateInclusive
        );
        if (count == null) {
            throw new IllegalStateException("product ranking source count is missing");
        }
        return count;
    }
}
