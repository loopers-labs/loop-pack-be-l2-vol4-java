package com.loopers.application.product;

import com.loopers.domain.product.SortOption;
import org.springframework.data.domain.Pageable;

import java.time.Duration;

public final class ProductCacheKeys {

    public static final Duration DETAIL_TTL = Duration.ofMinutes(5);
    public static final Duration LIST_TTL = Duration.ofMinutes(1);

    private ProductCacheKeys() {
    }

    public static String detail(Long productId) {
        return "product:detail:" + productId;
    }

    public static String list(Long brandId, SortOption sort, Pageable pageable) {
        String brand = brandId == null ? "all" : brandId.toString();
        return "product:list:" + brand + ":" + sort + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();
    }
}
