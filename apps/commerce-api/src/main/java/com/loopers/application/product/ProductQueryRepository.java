package com.loopers.application.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductQueryRepository {
    Page<ProductInfo> findAllWithDetails(Long brandId, Pageable pageable);
}
