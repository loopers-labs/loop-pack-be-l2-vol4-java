package com.loopers.stock.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductStockRepository {

    ProductStock save(ProductStock productStock);

    Optional<ProductStock> findByProductId(Long productId);

    List<ProductStock> findAllByProductIds(Collection<Long> productIds);

    List<ProductStock> findAllByProductIdsForUpdate(Collection<Long> productIds);
}
