package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingItemInfo(long rank, double score, ProductInfo product) {}
