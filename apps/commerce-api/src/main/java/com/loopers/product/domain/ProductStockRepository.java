package com.loopers.product.domain;

import java.util.List;
import java.util.Optional;

public interface ProductStockRepository {
    ProductStock save(ProductStock stock);
    Optional<ProductStock> findByProductId(Long productId);
    List<ProductStock> findAllByProductIdIn(List<Long> productIds);
    Optional<ProductStock> findByProductIdForUpdate(Long productId);
    /** 재고가 충분할 때만 원자적으로 차감한다. 차감된 행 수(1=성공, 0=재고부족 또는 상품없음)를 반환한다. */
    int decreaseStock(Long productId, int quantity);
    int softDeleteByBrandId(Long brandId);
}
