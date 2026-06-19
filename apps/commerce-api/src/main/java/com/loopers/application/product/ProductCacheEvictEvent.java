package com.loopers.application.product;

import java.util.List;

public record ProductCacheEvictEvent(List<Long> productIds) {
}
