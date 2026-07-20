package com.loopers.application.catalog.ranking;

import com.loopers.application.catalog.product.ProductResult;

public record RankingResult(
    Long rank,
    Double score,
    ProductResult product
) {
}
