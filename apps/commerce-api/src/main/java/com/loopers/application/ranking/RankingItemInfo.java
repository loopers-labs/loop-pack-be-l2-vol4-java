package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingItemInfo(Long rank, Double score, ProductInfo product) {
}
