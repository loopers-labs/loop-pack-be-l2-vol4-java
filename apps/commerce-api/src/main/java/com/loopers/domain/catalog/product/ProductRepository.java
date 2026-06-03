package com.loopers.domain.catalog.product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);

    Optional<Product> find(Long id);

    Optional<Product> findOnSale(Long id);

    List<Product> findAllByIds(Collection<Long> ids);

    default List<Product> findAllByIdsForUpdate(Collection<Long> ids) {
        return findAllByIds(ids);
    }

    List<Product> findByBrandId(Long brandId);

    List<Product> search(ProductSearchCondition condition);

    long count(ProductSearchCondition condition);

    int increaseLikeCount(Long productId);

    int decreaseLikeCount(Long productId);
}
