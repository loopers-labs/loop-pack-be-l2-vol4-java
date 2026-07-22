package com.loopers.ranking.application;

import com.loopers.product.application.ProductDetailInfo;

public record RankingItemInfo(long rank, double score, ProductDetailInfo product) {}
