package com.loopers.application.product;

import java.util.List;

public record ProductListPage(List<ProductInfo> content, long totalElements) {
}
