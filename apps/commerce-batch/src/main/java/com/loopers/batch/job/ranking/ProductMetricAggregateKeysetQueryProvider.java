package com.loopers.batch.job.ranking;

import org.springframework.batch.item.database.Order;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.batch.item.database.support.SqlPagingQueryUtils;

import java.util.Map;

final class ProductMetricAggregateKeysetQueryProvider extends MySqlPagingQueryProvider {

    ProductMetricAggregateKeysetQueryProvider() {
        setSelectClause("""
            product_id,
            sum(view_count) as view_count,
            sum(like_delta) as like_delta,
            sum(order_amount) as order_amount
            """);
        setFromClause("product_metrics");
        setWhereClause(
            "metric_date between :periodStart and :aggregationEndDate"
        );
        setGroupClause("product_id");
        setSortKeys(Map.of("product_id", Order.ASCENDING));
    }

    @Override
    public String generateRemainingPagesQuery(int pageSize) {
        return SqlPagingQueryUtils.generateLimitSqlQuery(
            this,
            true,
            "LIMIT " + pageSize
        );
    }
}
