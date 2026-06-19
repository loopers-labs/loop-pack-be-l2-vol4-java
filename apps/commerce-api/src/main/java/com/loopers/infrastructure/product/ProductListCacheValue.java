package com.loopers.infrastructure.product;

import com.loopers.application.product.ProductInfo;

import java.util.List;

public record ProductListCacheValue(List<ProductInfo> content, long totalElements) {
}
