package com.loopers.batch.job.productrank;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class ProductRankScoreProcessor implements ItemProcessor<ProductMetricDailyRow, ProductRankScoreDelta> {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double PURCHASE_WEIGHT = 0.7;

    @Override
    public ProductRankScoreDelta process(ProductMetricDailyRow row) {
        double scoreDelta = row.viewCount() * VIEW_WEIGHT
                + row.likeDeltaCount() * LIKE_WEIGHT
                + row.purchaseQuantity() * PURCHASE_WEIGHT;

        return new ProductRankScoreDelta(
                row.productId(),
                scoreDelta,
                row.viewCount(),
                row.likeDeltaCount(),
                row.purchaseQuantity()
        );
    }
}
