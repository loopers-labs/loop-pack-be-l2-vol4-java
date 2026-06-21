package com.loopers.product.infrastructure;

import com.loopers.product.domain.Product;
import com.loopers.product.domain.ProductSortOption;

import java.util.List;

public interface ProductJpaRepositoryCustom {
    List<Product> findAllOnSale(Long brandId, ProductSortOption sort, long offset, int limit);
    long countOnSale(Long brandId);
}
