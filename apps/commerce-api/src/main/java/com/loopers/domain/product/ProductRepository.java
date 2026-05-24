package com.loopers.domain.product;

import com.loopers.support.pagination.PageQuery;
import com.loopers.support.pagination.PageResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findActiveById(Long productId);

    List<Product> findActiveAllByIds(Collection<Long> productIds);

    List<Product> findActiveAllByBrandId(Long brandId);

    PageResult<Product> findActiveAll(PageQuery query, Long brandId);

    PageResult<Product> findVisibleAll(PageQuery query, Long brandId, ProductSort sort);

    PageResult<Product> findVisibleLikedAllByUserId(Long userId, PageQuery query);
}
