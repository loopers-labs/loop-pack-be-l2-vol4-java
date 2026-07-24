package com.loopers.metrics.infrastructure;

import com.loopers.metrics.application.ProductMetricDelta;
import com.loopers.metrics.application.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@RequiredArgsConstructor
@Component
public class JdbcProductMetricsRepository implements ProductMetricsRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void addAll(List<ProductMetricDelta> deltas, Instant updatedAt) {
        if (deltas.isEmpty()) {
            return;
        }

        LocalDateTime updatedAtUtc = LocalDateTime.ofInstant(updatedAt, ZoneOffset.UTC);
        List<Object[]> batchArguments = deltas.stream()
            .map(delta -> new Object[]{
                delta.metricDate(),
                delta.productId(),
                delta.viewCountDelta(),
                delta.likeDelta(),
                delta.orderQuantityDelta(),
                delta.orderAmountDelta(),
                updatedAtUtc,
                delta.viewCountDelta(),
                delta.likeDelta(),
                delta.orderQuantityDelta(),
                delta.orderAmountDelta(),
                updatedAtUtc
            })
            .toList();

        jdbcTemplate.batchUpdate("""
                insert into product_metrics(
                    metric_date,
                    product_id,
                    view_count,
                    like_delta,
                    order_quantity,
                    order_amount,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on duplicate key update
                    view_count = view_count + ?,
                    like_delta = like_delta + ?,
                    order_quantity = order_quantity + ?,
                    order_amount = order_amount + ?,
                    updated_at = ?
                """,
            batchArguments);
    }
}
