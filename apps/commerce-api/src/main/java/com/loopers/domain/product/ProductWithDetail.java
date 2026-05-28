package com.loopers.domain.product;

import com.loopers.domain.brand.BrandModel;

public record ProductWithDetail(ProductModel product, BrandModel brand, long likeCount) {}