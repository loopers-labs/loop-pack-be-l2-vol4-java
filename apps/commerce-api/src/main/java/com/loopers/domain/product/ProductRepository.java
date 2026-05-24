package com.loopers.domain.product;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findActiveById(Long productId);

    PageResult<Product> findActiveAll(PageQuery query, Long brandId);

    PageResult<Product> findVisibleAll(PageQuery query, Long brandId, ProductSort sort);
}
